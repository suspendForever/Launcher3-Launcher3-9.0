/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.model;

import static com.android.launcher3.ItemInfoWithIcon.FLAG_DISABLED_LOCKED_USER;
import static com.android.launcher3.ItemInfoWithIcon.FLAG_DISABLED_SAFEMODE;
import static com.android.launcher3.ItemInfoWithIcon.FLAG_DISABLED_SUSPENDED;
import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW;
import static com.android.launcher3.model.LoaderResults.filterCurrentWorkspaceItems;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionInfo;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Process;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.MutableInt;

import com.android.launcher3.AllAppsList;
import com.android.launcher3.AppInfo;
import com.android.launcher3.FolderInfo;
import com.android.launcher3.IconCache;
import com.android.launcher3.InstallShortcutReceiver;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherAppWidgetInfo;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.PackageInstallerCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.developerspace.LogUtil;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIconPreviewVerifier;
import com.android.launcher3.graphics.LauncherIcons;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.provider.ImportDataTask;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.LooperIdleLock;
import com.android.launcher3.util.MultiHashMap;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.Provider;
import com.android.launcher3.util.TraceHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

/**
 * Runnable for the thread that loads the contents of the launcher:
 * - workspace icons
 * - widgets
 * - all apps icons
 * - deep shortcuts within apps
 */
public class LoaderTask implements Runnable {
    private static final String TAG = "LoaderTask";

    private final LauncherAppState mApp;
    private final AllAppsList mBgAllAppsList;
    private final BgDataModel mBgDataModel;

    private FirstScreenBroadcast mFirstScreenBroadcast;

    private final LoaderResults mResults;

    private final LauncherAppsCompat mLauncherApps;
    private final UserManagerCompat mUserManager;
    private final DeepShortcutManager mShortcutManager;
    private final PackageInstallerCompat mPackageInstaller;
    private final AppWidgetManagerCompat mAppWidgetManager;
    private final IconCache mIconCache;

    private boolean mStopped;

    public LoaderTask(LauncherAppState app, AllAppsList bgAllAppsList, BgDataModel dataModel,
                      LoaderResults results) {
        mApp = app;
        mBgAllAppsList = bgAllAppsList;
        mBgDataModel = dataModel;
        mResults = results;

        mLauncherApps = LauncherAppsCompat.getInstance(mApp.getContext());
        mUserManager = UserManagerCompat.getInstance(mApp.getContext());
        mShortcutManager = DeepShortcutManager.getInstance(mApp.getContext());
        mPackageInstaller = PackageInstallerCompat.getInstance(mApp.getContext());
        mAppWidgetManager = AppWidgetManagerCompat.getInstance(mApp.getContext());
        mIconCache = mApp.getIconCache();
    }

    protected synchronized void waitForIdle() {
        // Wait until the either we're stopped or the other threads are done.
        // This way we don't start loading all apps until the workspace has settled
        // down.
        LooperIdleLock idleLock = mResults.newIdleLock(this);
        // Just in case mFlushingWorkerThread changes but we aren't woken up,
        // wait no longer than 1sec at a time
        while (!mStopped && idleLock.awaitLocked(1000)) ;
    }

    private synchronized void verifyNotStopped() throws CancellationException {
        if (mStopped) {
            throw new CancellationException("Loader stopped");
        }
    }

    private void sendFirstScreenActiveInstallsBroadcast() {
        ArrayList<ItemInfo> firstScreenItems = new ArrayList<>();

        ArrayList<ItemInfo> allItems = new ArrayList<>();
        synchronized (mBgDataModel) {
            allItems.addAll(mBgDataModel.workspaceItems);
            allItems.addAll(mBgDataModel.appWidgets);
        }
        long firstScreen = mBgDataModel.workspaceScreens.isEmpty()
                ? -1 // In this case, we can still look at the items in the hotseat.
                : mBgDataModel.workspaceScreens.get(0);
        filterCurrentWorkspaceItems(firstScreen, allItems, firstScreenItems,
                new ArrayList<>() /* otherScreenItems are ignored */);
        mFirstScreenBroadcast.sendBroadcasts(mApp.getContext(), firstScreenItems);
    }

    public void run() {
        synchronized (this) {
            // Skip fast if we are already stopped.
            if (mStopped) {
                return;
            }
        }

        TraceHelper.beginSection(TAG);
        try (LauncherModel.LoaderTransaction transaction = mApp.getModel().beginLoader(this)) {
            TraceHelper.partitionSection(TAG, "step 1.1: loading workspace");
            loadWorkspace();

            verifyNotStopped();
            TraceHelper.partitionSection(TAG, "step 1.2: bind workspace workspace");
            mResults.bindWorkspace();

            // Notify the installer packages of packages with active installs on the first screen.
            TraceHelper.partitionSection(TAG, "step 1.3: send first screen broadcast");
            sendFirstScreenActiveInstallsBroadcast();

            // Take a break
            TraceHelper.partitionSection(TAG, "step 1 completed, wait for idle");
            waitForIdle();
            verifyNotStopped();

            // second step
            TraceHelper.partitionSection(TAG, "step 2.1: loading all apps");
            loadAllApps();

            TraceHelper.partitionSection(TAG, "step 2.2: Binding all apps");
            verifyNotStopped();
            mResults.bindAllApps();

            verifyNotStopped();
            TraceHelper.partitionSection(TAG, "step 2.3: Update icon cache");
            updateIconCache();

            // Take a break
            TraceHelper.partitionSection(TAG, "step 2 completed, wait for idle");
            waitForIdle();
            verifyNotStopped();

            // third step
            TraceHelper.partitionSection(TAG, "step 3.1: loading deep shortcuts");
            loadDeepShortcuts();

            verifyNotStopped();
            TraceHelper.partitionSection(TAG, "step 3.2: bind deep shortcuts");
            mResults.bindDeepShortcuts();

            // Take a break
            TraceHelper.partitionSection(TAG, "step 3 completed, wait for idle");
            waitForIdle();
            verifyNotStopped();

            // fourth step
            TraceHelper.partitionSection(TAG, "step 4.1: loading widgets");
            mBgDataModel.widgetsModel.update(mApp, null);

            verifyNotStopped();
            TraceHelper.partitionSection(TAG, "step 4.2: Binding widgets");
            mResults.bindWidgets();

            transaction.commit();
        } catch (CancellationException e) {
            // Loader stopped, ignore
            TraceHelper.partitionSection(TAG, "Cancelled");
        }
        TraceHelper.endSection(TAG);
    }

    public synchronized void stopLocked() {
        mStopped = true;
        this.notify();
    }

    //Method 'loadWorkspace' is too complex to analyze by data flow algorithm
    private void loadWorkspace() {
        final Context context = mApp.getContext();
        final ContentResolver contentResolver = context.getContentResolver();
        final PackageManagerHelper pmHelper = new PackageManagerHelper(context);
        final boolean isSafeMode = pmHelper.isSafeMode();
        final boolean isSdCardReady = Utilities.isBootCompleted();
        final MultiHashMap<UserHandle, String> pendingPackages = new MultiHashMap<>();

        boolean clearDb = false;

        try {
            boolean result = ImportDataTask.performImportIfPossible(context);
            LogUtil.d(TAG, "performImportIfPossible result: " + result);
        } catch (Exception e) {
            // Migration failed. Clear workspace.
            clearDb = true;
        }

        //迁移workspace和hotseat 因为有可能行数和列数放生了变化，如果没发生变化就不用迁移，一般没有变化 走不进去
        if (!clearDb && GridSizeMigrationTask.ENABLED &&
                !GridSizeMigrationTask.migrateGridIfNeeded(context)) {
            // Migration failed. Clear workspace.
            clearDb = true;
        }

        if (clearDb) {
            Log.d(TAG, "loadWorkspace: resetting launcher database");
            LauncherSettings.Settings.call(contentResolver,
                    LauncherSettings.Settings.METHOD_CREATE_EMPTY_DB);
        }

        //加载默认布局
        Log.d(TAG, "loadWorkspace: loading default favorites");
        LauncherSettings.Settings.call(contentResolver,
                LauncherSettings.Settings.METHOD_LOAD_DEFAULT_FAVORITES);

        synchronized (mBgDataModel) {
            mBgDataModel.clear();

            final HashMap<String, SessionInfo> installingPkgs =
                    mPackageInstaller.updateAndGetActiveSessionCache();
            mFirstScreenBroadcast = new FirstScreenBroadcast(installingPkgs);
            //获取数据库的屏幕id排序合集
            mBgDataModel.workspaceScreens.addAll(LauncherModel.loadWorkspaceScreensDb(context));

            Map<ShortcutKey, ShortcutInfoCompat> shortcutKeyToPinnedShortcuts = new HashMap<>();
            //查询数据库所有数据
            final LoaderCursor loaderCursor = new LoaderCursor(contentResolver.query(
                    LauncherSettings.Favorites.CONTENT_URI, null, null, null, null), mApp);

            HashMap<ComponentKey, AppWidgetProviderInfo> widgetProvidersMap = null;

            //获取字段的index值 方便后面查询
            try {
                final int appWidgetIdIndex = loaderCursor.getColumnIndexOrThrow(
                        LauncherSettings.Favorites.APPWIDGET_ID);
                final int appWidgetProviderIndex = loaderCursor.getColumnIndexOrThrow(
                        LauncherSettings.Favorites.APPWIDGET_PROVIDER);
                final int spanXIndex = loaderCursor.getColumnIndexOrThrow
                        (LauncherSettings.Favorites.SPANX);
                final int spanYIndex = loaderCursor.getColumnIndexOrThrow(
                        LauncherSettings.Favorites.SPANY);
                final int rankIndex = loaderCursor.getColumnIndexOrThrow(
                        LauncherSettings.Favorites.RANK);
                final int optionsIndex = loaderCursor.getColumnIndexOrThrow(
                        LauncherSettings.Favorites.OPTIONS);

                final LongSparseArray<UserHandle> allUsers = loaderCursor.allUsers;
                final LongSparseArray<Boolean> quietMode = new LongSparseArray<>();
                final LongSparseArray<Boolean> unlockedUsers = new LongSparseArray<>();

                //和user相关
                List<UserHandle> userProfiles = mUserManager.getUserProfiles();
                for (UserHandle user : userProfiles) {
                    long serialNo = mUserManager.getSerialNumberForUser(user);
                    allUsers.put(serialNo, user);
                    quietMode.put(serialNo, mUserManager.isQuietModeEnabled(user));

                    boolean userUnlocked = mUserManager.isUserUnlocked(user);

                    // We can only query for shortcuts when the user is unlocked.
                    if (userUnlocked) {
                        //固定的快捷方式列表 一般为0
                        List<ShortcutInfoCompat> pinnedShortcuts =
                                mShortcutManager.queryForPinnedShortcuts(null, user);
                        if (mShortcutManager.wasLastCallSuccess()) {
                            for (ShortcutInfoCompat shortcut : pinnedShortcuts) {
                                shortcutKeyToPinnedShortcuts.put(ShortcutKey.fromInfo(shortcut),
                                        shortcut);
                            }
                        } else {
                            // Shortcut manager can fail due to some race condition when the
                            // lock state changes too frequently. For the purpose of the loading
                            // shortcuts, consider the user is still locked.
                            userUnlocked = false;
                        }
                    }
                    unlockedUsers.put(serialNo, userUnlocked);
                }

                ShortcutInfo info;
                LauncherAppWidgetInfo appWidgetInfo;
                Intent intent;
                String targetPkg;

                FolderIconPreviewVerifier verifier =
                        new FolderIconPreviewVerifier(mApp.getInvariantDeviceProfile());
                //循环遍历数据库的数据
                while (!mStopped && loaderCursor.moveToNext()) {
                    try {
                        //如果user为空 直接跳过这条数据
                        if (loaderCursor.user == null) {
                            // User has been deleted, remove the item.
                            loaderCursor.markDeleted("User has been deleted");
                            continue;
                        }

                        boolean allowMissingTarget = false;
                        switch (loaderCursor.itemType) {
                            case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                            case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                            case LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT: {
                                //url转换为intent
                                intent = loaderCursor.parseIntent();
                                //如果intent 为空 跳过这次循环
                                if (intent == null) {
                                    loaderCursor.markDeleted("Invalid or null intent");
                                    continue;
                                }

                                int disabledState = quietMode.get(loaderCursor.serialNumber) ?
                                        ShortcutInfo.FLAG_DISABLED_QUIET_USER : 0;
                                ComponentName cn = intent.getComponent();
                                targetPkg = cn == null ? intent.getPackage() : cn.getPackageName();

                                //如果该数据的user不是当前user
                                if (!Process.myUserHandle().equals(loaderCursor.user)) {
                                    //非当前user 如果该条数据是快捷方式 删除这条数据
                                    if (loaderCursor.itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT) {
                                        loaderCursor.markDeleted("Legacy shortcuts are only allowed for default user");
                                        continue;
                                    } else if (loaderCursor.restoreFlag != 0) {
                                        // Don't restore items for other profiles.
                                        //如果这条数据的restoreflag不为0 删除这条数据
                                        loaderCursor.markDeleted("Restore from managed profile not supported");
                                        continue;
                                    }
                                }
                                //如果intent的所含包名为空而且这条数据不是快捷方式 删除这条数据
                                if (TextUtils.isEmpty(targetPkg) &&
                                        loaderCursor.itemType != LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT) {
                                    loaderCursor.markDeleted("Only legacy shortcuts can have null package");
                                    continue;
                                }

                                // If there is no target package, its an implicit intent
                                //如果没有目标包，这是一个隐式意图
                                // (legacy shortcut) which is always valid
                                //（传统快捷方式）始终有效
                                boolean validTarget = TextUtils.isEmpty(targetPkg) ||
                                        mLauncherApps.isPackageEnabledForProfile(targetPkg, loaderCursor.user);


                                // If the apk is present and the shortcut points to a specific component.
                                //如果apk存在并且快捷方式指向特定组件。
                                // If the component is already present
                                //如果当前component存在
                                if (cn != null && validTarget) {
                                    //如果这条数据activity 存在
                                    if (mLauncherApps.isActivityEnabledForProfile(cn, loaderCursor.user)) {
                                        // no special handling necessary for this item
                                        //将该项数据标记为恢复
                                        loaderCursor.markRestored();
                                    } else {
                                        if (loaderCursor.hasRestoreFlag(ShortcutInfo.FLAG_AUTOINSTALL_ICON)) {
                                            // We allow auto install apps to have their intent updated after an install.
                                            //我们允许自动安装应用程序在安装后更新其意图
                                            intent = pmHelper.getAppLaunchIntent(targetPkg, loaderCursor.user);

                                            //如果这个数据没有launcherIntent 删掉
                                            if (intent != null) {
                                                loaderCursor.restoreFlag = 0;
                                                loaderCursor.updater().put(
                                                        LauncherSettings.Favorites.INTENT,
                                                        intent.toUri(0)).commit();
                                                cn = intent.getComponent();
                                            } else {
                                                loaderCursor.markDeleted("Unable to find a launch target");
                                                continue;
                                            }
                                        } else {
                                            //说明该条数据的隐式意图无效
                                            // The app is installed but the component is no
                                            // longer available.
                                            loaderCursor.markDeleted("Invalid component removed: " + cn);
                                            continue;
                                        }
                                    }
                                }
                                // else if cn == null => can't infer much, leave it
                                // else if !validPkg => could be restored icon or missing sd-card

                                if (!TextUtils.isEmpty(targetPkg) && !validTarget) {
                                    //说明是一个显示意图
                                    // Points to a valid app (superset of cn != null) but the apk is not available.

                                    if (loaderCursor.restoreFlag != 0) {
                                        //程序包尚不可用，但可能稍后安装
                                        // Package is not yet available but might be
                                        // installed later.
                                        FileLog.d(TAG, "package not yet restored: " + targetPkg);

                                        if (loaderCursor.hasRestoreFlag(ShortcutInfo.FLAG_RESTORE_STARTED)) {
                                            // Restore has started once.
                                        } else if (installingPkgs.containsKey(targetPkg)) {
                                            // App restore has started. Update the flag
                                            //如果目标包正在安装 标记为restore 更新数据库
                                            loaderCursor.restoreFlag |= ShortcutInfo.FLAG_RESTORE_STARTED;
                                            loaderCursor.updater().commit();
                                        } else {
                                            //说明这个包不会恢复了 删除
                                            loaderCursor.markDeleted("Unrestored app removed: " + targetPkg);
                                            continue;
                                        }
                                    } else if (pmHelper.isAppOnSdcard(targetPkg, loaderCursor.user)) {
                                        // Package is present but not available.
                                        // 包存在，但不可用。
                                        disabledState |= ShortcutInfo.FLAG_DISABLED_NOT_AVAILABLE;
                                        // Add the icon on the workspace anyway.
                                        //在工作区中添加图标。
                                        allowMissingTarget = true;
                                    } else if (!isSdCardReady) {
                                        // SdCard is not ready yet. Package might get available,
                                        //SdCard尚未准备好。包可能可用，
                                        // once it is ready.
                                        Log.d(TAG, "Missing pkg, will check later: " + targetPkg);
                                        pendingPackages.addToList(loaderCursor.user, targetPkg);
                                        // Add the icon on the workspace anyway.
                                        //在工作区中添加图标。
                                        allowMissingTarget = true;
                                    } else {
                                        //不再等待外部设备加载。 删除
                                        // Do not wait for external media load anymore.
                                        loaderCursor.markDeleted("Invalid package removed: " + targetPkg);
                                        continue;
                                    }
                                }

                                //这条数据可能稍后安装而且支持webui
                                if ((loaderCursor.restoreFlag & ShortcutInfo.FLAG_SUPPORTS_WEB_UI) != 0) {
                                    //说明不是隐式意图
                                    validTarget = false;
                                }

                                if (validTarget) {
                                    // The shortcut points to a valid target (either no target
                                    // or something which is ready to be used)
                                    //快捷方式指向一个有效的目标（要么没有目标，要么是准备使用的东西）标记为恢复
                                    loaderCursor.markRestored();
                                }

                                boolean useLowResIcon = !loaderCursor.isOnWorkspaceOrHotseat() &&
                                        !verifier.isItemInPreview(loaderCursor.getInt(rankIndex));

                                // 已在上面验证该用户与默认用户相同
                                //从现在开始获取生成该条数据的shortcutinfo 添加到数据model中
                                if (loaderCursor.restoreFlag != 0) {
                                    //生成restoredinfo 表示该info为系统上尚未安装的程序包。
                                    info = loaderCursor.getRestoredItemInfo(intent);
                                } else if (loaderCursor.itemType ==
                                        LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
                                    //为应用程序的快捷方式创建ShortcutInfo对象。
                                    info = loaderCursor.getAppShortcutInfo(
                                            intent, allowMissingTarget, useLowResIcon);
                                } else if (loaderCursor.itemType ==
                                        LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
                                    //创建固定快捷方式的shortcutInfo
                                    ShortcutKey key = ShortcutKey.fromIntent(intent, loaderCursor.user);
                                    if (unlockedUsers.get(loaderCursor.serialNumber)) {
                                        ShortcutInfoCompat pinnedShortcut =
                                                shortcutKeyToPinnedShortcuts.get(key);
                                        if (pinnedShortcut == null) {
                                            // The shortcut is no longer valid.
                                            loaderCursor.markDeleted("Pinned shortcut not found");
                                            continue;
                                        }
                                        info = new ShortcutInfo(pinnedShortcut, context);
                                        final ShortcutInfo finalInfo = info;
                                        Provider<Bitmap> fallbackIconProvider = new Provider<Bitmap>() {
                                            @Override
                                            public Bitmap get() {
                                                // If the pinned deep shortcut is no longer published,
                                                // use the last saved icon instead of the default.
                                                return loaderCursor.loadIcon(finalInfo)
                                                        ? finalInfo.iconBitmap : null;
                                            }
                                        };
                                        LauncherIcons li = LauncherIcons.obtain(context);
                                        li.createShortcutIcon(pinnedShortcut,
                                                true /* badged */, fallbackIconProvider).applyTo(info);
                                        li.recycle();
                                        if (pmHelper.isAppSuspended(
                                                pinnedShortcut.getPackage(), info.user)) {
                                            info.runtimeStatusFlags |= FLAG_DISABLED_SUSPENDED;
                                        }
                                        intent = info.intent;
                                    } else {
                                        // Create a shortcut info in disabled mode for now.
                                        info = loaderCursor.loadSimpleShortcut();
                                        info.runtimeStatusFlags |= FLAG_DISABLED_LOCKED_USER;
                                    }
                                } else { // item type == ITEM_TYPE_SHORTCUT
                                    info = loaderCursor.loadSimpleShortcut();

                                    // Shortcuts are only available on the primary profile
                                    if (!TextUtils.isEmpty(targetPkg)
                                            && pmHelper.isAppSuspended(targetPkg, loaderCursor.user)) {
                                        disabledState |= FLAG_DISABLED_SUSPENDED;
                                    }

                                    // App shortcuts that used to be automatically added to Launcher
                                    // didn't always have the correct intent flags set, so do that
                                    // here
                                    if (intent.getAction() != null &&
                                            intent.getCategories() != null &&
                                            intent.getAction().equals(Intent.ACTION_MAIN) &&
                                            intent.getCategories().contains(Intent.CATEGORY_LAUNCHER)) {
                                        intent.addFlags(
                                                Intent.FLAG_ACTIVITY_NEW_TASK |
                                                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                                    }
                                }

                                //将创建的shortcutinfo 添加一些属性 然后加到数据model中
                                if (info != null) {
                                    loaderCursor.applyCommonProperties(info);

                                    info.intent = intent;
                                    info.rank = loaderCursor.getInt(rankIndex);
                                    info.spanX = 1;
                                    info.spanY = 1;
                                    info.runtimeStatusFlags |= disabledState;
                                    if (isSafeMode && !Utilities.isSystemApp(context, intent)) {
                                        info.runtimeStatusFlags |= FLAG_DISABLED_SAFEMODE;
                                    }

                                    if (loaderCursor.restoreFlag != 0 && !TextUtils.isEmpty(targetPkg)) {
                                        SessionInfo si = installingPkgs.get(targetPkg);
                                        if (si == null) {
                                            info.status &= ~ShortcutInfo.FLAG_INSTALL_SESSION_ACTIVE;
                                        } else {
                                            info.setInstallProgress((int) (si.getProgress() * 100));
                                        }
                                    }

                                    loaderCursor.checkAndAddItem(info, mBgDataModel);
                                } else {
                                    throw new RuntimeException("Unexpected null ShortcutInfo");
                                }
                                break;
                            }

                            case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                                FolderInfo folderInfo = mBgDataModel.findOrMakeFolder(loaderCursor.id);
                                loaderCursor.applyCommonProperties(folderInfo);

                                // Do not trim the folder label, as is was set by the user.
                                folderInfo.title = loaderCursor.getString(loaderCursor.titleIndex);
                                folderInfo.spanX = 1;
                                folderInfo.spanY = 1;
                                folderInfo.options = loaderCursor.getInt(optionsIndex);

                                // no special handling required for restored folders
                                loaderCursor.markRestored();

                                loaderCursor.checkAndAddItem(folderInfo, mBgDataModel);
                                break;

                            case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                                if (FeatureFlags.GO_DISABLE_WIDGETS) {
                                    loaderCursor.markDeleted("Only legacy shortcuts can have null package");
                                    continue;
                                }
                                // Follow through
                            case LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET:
                                // Read all Launcher-specific widget details
                                boolean customWidget = loaderCursor.itemType ==
                                        LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET;

                                int appWidgetId = loaderCursor.getInt(appWidgetIdIndex);
                                String savedProvider = loaderCursor.getString(appWidgetProviderIndex);

                                final ComponentName component =
                                        ComponentName.unflattenFromString(savedProvider);

                                final boolean isIdValid = !loaderCursor.hasRestoreFlag(
                                        LauncherAppWidgetInfo.FLAG_ID_NOT_VALID);
                                final boolean wasProviderReady = !loaderCursor.hasRestoreFlag(
                                        LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY);

                                if (widgetProvidersMap == null) {
                                    widgetProvidersMap = mAppWidgetManager.getAllProvidersMap();
                                }
                                final AppWidgetProviderInfo provider = widgetProvidersMap.get(
                                        new ComponentKey(
                                                ComponentName.unflattenFromString(savedProvider),
                                                loaderCursor.user));

                                final boolean isProviderReady = isValidProvider(provider);
                                if (!isSafeMode && !customWidget &&
                                        wasProviderReady && !isProviderReady) {
                                    loaderCursor.markDeleted(
                                            "Deleting widget that isn't installed anymore: "
                                                    + provider);
                                } else {
                                    if (isProviderReady) {
                                        appWidgetInfo = new LauncherAppWidgetInfo(appWidgetId,
                                                provider.provider);

                                        // The provider is available. So the widget is either
                                        // available or not available. We do not need to track
                                        // any future restore updates.
                                        int status = loaderCursor.restoreFlag &
                                                ~LauncherAppWidgetInfo.FLAG_RESTORE_STARTED &
                                                ~LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY;
                                        if (!wasProviderReady) {
                                            // If provider was not previously ready, update the
                                            // status and UI flag.

                                            // Id would be valid only if the widget restore broadcast was received.
                                            if (isIdValid) {
                                                status |= LauncherAppWidgetInfo.FLAG_UI_NOT_READY;
                                            }
                                        }
                                        appWidgetInfo.restoreStatus = status;
                                    } else {
                                        Log.v(TAG, "Widget restore pending id=" + loaderCursor.id
                                                + " appWidgetId=" + appWidgetId
                                                + " status =" + loaderCursor.restoreFlag);
                                        appWidgetInfo = new LauncherAppWidgetInfo(appWidgetId,
                                                component);
                                        appWidgetInfo.restoreStatus = loaderCursor.restoreFlag;
                                        SessionInfo si =
                                                installingPkgs.get(component.getPackageName());
                                        Integer installProgress = si == null
                                                ? null
                                                : (int) (si.getProgress() * 100);

                                        if (loaderCursor.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_RESTORE_STARTED)) {
                                            // Restore has started once.
                                        } else if (installProgress != null) {
                                            // App restore has started. Update the flag
                                            appWidgetInfo.restoreStatus |=
                                                    LauncherAppWidgetInfo.FLAG_RESTORE_STARTED;
                                        } else if (!isSafeMode) {
                                            loaderCursor.markDeleted("Unrestored widget removed: " + component);
                                            continue;
                                        }

                                        appWidgetInfo.installProgress =
                                                installProgress == null ? 0 : installProgress;
                                    }
                                    if (appWidgetInfo.hasRestoreFlag(
                                            LauncherAppWidgetInfo.FLAG_DIRECT_CONFIG)) {
                                        appWidgetInfo.bindOptions = loaderCursor.parseIntent();
                                    }

                                    loaderCursor.applyCommonProperties(appWidgetInfo);
                                    appWidgetInfo.spanX = loaderCursor.getInt(spanXIndex);
                                    appWidgetInfo.spanY = loaderCursor.getInt(spanYIndex);
                                    appWidgetInfo.user = loaderCursor.user;

                                    if (!loaderCursor.isOnWorkspaceOrHotseat()) {
                                        loaderCursor.markDeleted("Widget found where container != " +
                                                "CONTAINER_DESKTOP nor CONTAINER_HOTSEAT - ignoring!");
                                        continue;
                                    }

                                    if (!customWidget) {
                                        String providerName =
                                                appWidgetInfo.providerName.flattenToString();
                                        if (!providerName.equals(savedProvider) ||
                                                (appWidgetInfo.restoreStatus != loaderCursor.restoreFlag)) {
                                            loaderCursor.updater()
                                                    .put(LauncherSettings.Favorites.APPWIDGET_PROVIDER,
                                                            providerName)
                                                    .put(LauncherSettings.Favorites.RESTORED,
                                                            appWidgetInfo.restoreStatus)
                                                    .commit();
                                        }
                                    }

                                    if (appWidgetInfo.restoreStatus !=
                                            LauncherAppWidgetInfo.RESTORE_COMPLETED) {
                                        String pkg = appWidgetInfo.providerName.getPackageName();
                                        appWidgetInfo.pendingItemInfo = new PackageItemInfo(pkg);
                                        appWidgetInfo.pendingItemInfo.user = appWidgetInfo.user;
                                        mIconCache.getTitleAndIconForApp(
                                                appWidgetInfo.pendingItemInfo, false);
                                    }

                                    loaderCursor.checkAndAddItem(appWidgetInfo, mBgDataModel);
                                }
                                break;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Desktop items loading interrupted", e);
                    }
                }
            } finally {
                Utilities.closeSilently(loaderCursor);
            }

            // Break early if we've stopped loading
            if (mStopped) {
                mBgDataModel.clear();
                return;
            }

            // Remove dead items
            //删除标记为删除的数据
            if (loaderCursor.commitDeleted()) {
                // Remove any empty folder
                //删除空文件夹
                ArrayList<Long> deletedFolderIds = (ArrayList<Long>) LauncherSettings.Settings
                        .call(contentResolver,
                                LauncherSettings.Settings.METHOD_DELETE_EMPTY_FOLDERS)
                        .getSerializable(LauncherSettings.Settings.EXTRA_VALUE);
                for (long folderId : deletedFolderIds) {
                    mBgDataModel.workspaceItems.remove(mBgDataModel.folders.get(folderId));
                    mBgDataModel.folders.remove(folderId);
                    mBgDataModel.itemsIdMap.remove(folderId);
                }

                // Remove any ghost widgets
                LauncherSettings.Settings.call(contentResolver,
                        LauncherSettings.Settings.METHOD_REMOVE_GHOST_WIDGETS);
            }

            // Unpin shortcuts that don't exist on the workspace.
            HashSet<ShortcutKey> pendingShortcuts =
                    InstallShortcutReceiver.getPendingShortcuts(context);
            for (ShortcutKey key : shortcutKeyToPinnedShortcuts.keySet()) {
                MutableInt numTimesPinned = mBgDataModel.pinnedShortcutCounts.get(key);
                if ((numTimesPinned == null || numTimesPinned.value == 0)
                        && !pendingShortcuts.contains(key)) {
                    // Shortcut is pinned but doesn't exist on the workspace; unpin it.
                    mShortcutManager.unpinShortcut(key);
                }
            }

            FolderIconPreviewVerifier verifier =
                    new FolderIconPreviewVerifier(mApp.getInvariantDeviceProfile());
            // Sort the folder items and make sure all items in the preview are high resolution.
            for (FolderInfo folder : mBgDataModel.folders) {
                Collections.sort(folder.contents, Folder.ITEM_POS_COMPARATOR);
                verifier.setFolderInfo(folder);

                int numItemsInPreview = 0;
                for (ShortcutInfo info : folder.contents) {
                    if (info.usingLowResIcon
                            && info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
                            && verifier.isItemInPreview(info.rank)) {
                        mIconCache.getTitleAndIcon(info, false);
                        numItemsInPreview++;
                    }

                    if (numItemsInPreview >= MAX_NUM_ITEMS_IN_PREVIEW) {
                        break;
                    }
                }
            }

            loaderCursor.commitRestoredItems();
            if (!isSdCardReady && !pendingPackages.isEmpty()) {
                context.registerReceiver(
                        new SdCardAvailableReceiver(mApp, pendingPackages),
                        new IntentFilter(Intent.ACTION_BOOT_COMPLETED),
                        null,
                        new Handler(LauncherModel.getWorkerLooper()));
            }

            // Remove any empty screens
            ArrayList<Long> unusedScreens = new ArrayList<>(mBgDataModel.workspaceScreens);
            for (ItemInfo item : mBgDataModel.itemsIdMap) {
                long screenId = item.screenId;
                if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP &&
                        unusedScreens.contains(screenId)) {
                    unusedScreens.remove(screenId);
                }
            }

            // If there are any empty screens remove them, and update.
            if (unusedScreens.size() != 0) {
                mBgDataModel.workspaceScreens.removeAll(unusedScreens);
                LauncherModel.updateWorkspaceScreenOrder(context, mBgDataModel.workspaceScreens);
            }
        }
    }

    private void updateIconCache() {
        // Ignore packages which have a promise icon.
        HashSet<String> packagesToIgnore = new HashSet<>();
        synchronized (mBgDataModel) {
            for (ItemInfo info : mBgDataModel.itemsIdMap) {
                if (info instanceof ShortcutInfo) {
                    ShortcutInfo si = (ShortcutInfo) info;
                    if (si.isPromise() && si.getTargetComponent() != null) {
                        packagesToIgnore.add(si.getTargetComponent().getPackageName());
                    }
                } else if (info instanceof LauncherAppWidgetInfo) {
                    LauncherAppWidgetInfo lawi = (LauncherAppWidgetInfo) info;
                    if (lawi.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY)) {
                        packagesToIgnore.add(lawi.providerName.getPackageName());
                    }
                }
            }
        }
        mIconCache.updateDbIcons(packagesToIgnore);
    }

    private void loadAllApps() {
        final List<UserHandle> profiles = mUserManager.getUserProfiles();

        // Clear the list of apps
        mBgAllAppsList.clear();
        for (UserHandle user : profiles) {
            // Query for the set of apps
            final List<LauncherActivityInfo> apps = mLauncherApps.getActivityList(null, user);
            // Fail if we don't have any apps
            // TODO: Fix this. Only fail for the current user.
            if (apps == null || apps.isEmpty()) {
                return;
            }
            boolean quietMode = mUserManager.isQuietModeEnabled(user);
            // Create the ApplicationInfos
            for (int i = 0; i < apps.size(); i++) {
                LauncherActivityInfo app = apps.get(i);
                // This builds the icon bitmaps.
                mBgAllAppsList.add(new AppInfo(app, user, quietMode), app);
            }
        }

        if (FeatureFlags.LAUNCHER3_PROMISE_APPS_IN_ALL_APPS) {
            // get all active sessions and add them to the all apps list
            for (PackageInstaller.SessionInfo info :
                    mPackageInstaller.getAllVerifiedSessions()) {
                mBgAllAppsList.addPromiseApp(mApp.getContext(),
                        PackageInstallerCompat.PackageInstallInfo.fromInstallingState(info));
            }
        }

        mBgAllAppsList.added = new ArrayList<>();
    }

    private void loadDeepShortcuts() {
        mBgDataModel.deepShortcutMap.clear();
        mBgDataModel.hasShortcutHostPermission = mShortcutManager.hasHostPermission();
        if (mBgDataModel.hasShortcutHostPermission) {
            for (UserHandle user : mUserManager.getUserProfiles()) {
                if (mUserManager.isUserUnlocked(user)) {
                    List<ShortcutInfoCompat> shortcuts =
                            mShortcutManager.queryForAllShortcuts(user);
                    mBgDataModel.updateDeepShortcutMap(null, user, shortcuts);
                }
            }
        }
    }

    public static boolean isValidProvider(AppWidgetProviderInfo provider) {
        return (provider != null) && (provider.provider != null)
                && (provider.provider.getPackageName() != null);
    }
}
