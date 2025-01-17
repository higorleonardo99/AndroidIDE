/*
 * This file is part of AndroidIDE.
 *
 * AndroidIDE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AndroidIDE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 *
 */
package com.itsaky.androidide.fragments.preferences;

import static com.itsaky.androidide.managers.PreferenceManager.KEY_TP_FIX;

import android.os.Build;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreference;

import com.blankj.utilcode.util.FileUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.itsaky.androidide.R;
import com.itsaky.androidide.app.BaseApplication;
import com.itsaky.androidide.databinding.LayoutDialogTextInputBinding;
import com.itsaky.androidide.fragments.sheets.ProgressSheet;
import com.itsaky.androidide.tasks.TaskExecutor;
import com.itsaky.androidide.utils.DialogUtils;
import com.itsaky.androidide.utils.Environment;
import com.itsaky.androidide.utils.ILogger;

import java.io.File;
import java.util.Objects;

public class BuildPreferences extends BasePreferenceFragment
    implements Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener {

  public static final String KEY_GRADLE_COMMANDS = "idepref_build_gradleCommands";
  public static final String KEY_GRADLE_CLEAR_CACHE = "idepref_build_gradleClearCache";
  public static final String KEY_CUSTOM_GRADLE_INSTALLATION =
      "idepref_build_customGradleInstallation";
  private static final ILogger LOG = ILogger.newInstance("BuildPreferences");

  private ProgressSheet progressSheet;

  @Override
  public void onCreatePreferences(Bundle p1, String p2) {
    super.onCreatePreferences(p1, p2);
    if (getContext() == null) return;
    final var screen = getPreferenceScreen();
    final var categoryGradle = new PreferenceCategory(getContext());
    final var customCommands = new Preference(getContext());
    final var customInstallation = new Preference(getContext());
    final var clearCache = new Preference(getContext());

    screen.addPreference(categoryGradle);

    customCommands.setKey(KEY_GRADLE_COMMANDS);
    customCommands.setIcon(R.drawable.ic_bash_commands);
    customCommands.setTitle(R.string.idepref_build_customgradlecommands_title);
    customCommands.setSummary(R.string.idepref_build_customgradlecommands_summary);

    customInstallation.setKey(KEY_CUSTOM_GRADLE_INSTALLATION);
    customInstallation.setIcon(R.drawable.ic_gradle);
    customInstallation.setTitle(getString(R.string.idepref_title_customGradleInstallation));
    customInstallation.setSummary(getString(R.string.idepref_msg_customGradleInstallation));

    clearCache.setKey(KEY_GRADLE_CLEAR_CACHE);
    clearCache.setIcon(R.drawable.ic_delete);
    clearCache.setTitle(R.string.idepref_build_clearCache_title);
    clearCache.setSummary(R.string.idepref_build_clearCache_summary);

    categoryGradle.setTitle(R.string.gradle);
    categoryGradle.addPreference(customCommands);
    categoryGradle.addPreference(customInstallation);
    categoryGradle.addPreference(clearCache);

    // Works only for Android 11
    if (BaseApplication.isAarch64() && Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
      final var tpFix = new SwitchPreference(getContext());
      tpFix.setKey(KEY_TP_FIX);
      tpFix.setIcon(R.drawable.ic_language_java);
      tpFix.setTitle(getString(R.string.idepref_title_tpFix));
      tpFix.setSummary(getString(R.string.idepref_msg_tpFix));
      tpFix.setChecked(getPrefManager().shouldUseLdPreload());
      tpFix.setOnPreferenceChangeListener(this);

      categoryGradle.addPreference(tpFix);
    }

    setPreferenceScreen(screen);

    customCommands.setOnPreferenceClickListener(this);
    customInstallation.setOnPreferenceClickListener(this);
    clearCache.setOnPreferenceClickListener(this);
  }

  @Override
  public boolean onPreferenceClick(Preference p1) {
    final String key = p1.getKey();
    switch (key) {
      case KEY_GRADLE_COMMANDS:
        showGradleCommandsDialog();
        break;
      case KEY_GRADLE_CLEAR_CACHE:
        showClearCacheDialog();
        break;
      case KEY_CUSTOM_GRADLE_INSTALLATION:
        changeGradleDist();
        break;
    }
    return true;
  }

  @Override
  public boolean onPreferenceChange(final Preference preference, final Object newValue) {
    final var key = preference.getKey();
    if (KEY_TP_FIX.equals(key)) {
      getPrefManager().putBoolean(KEY_TP_FIX, ((boolean) newValue));
    }
    return true;
  }

  private void changeGradleDist() {
    final var builder = DialogUtils.newMaterialDialogBuilder(getContext());
    final var binding = LayoutDialogTextInputBinding.inflate(getLayoutInflater());
    binding.name.setStartIconDrawable(R.drawable.ic_gradle);
    binding.name.setHint(R.string.msg_gradle_installation_path);
    binding.name.setHelperText(getString(R.string.msg_gradle_installation_input_help));
    binding.name.setCounterEnabled(false);
    Objects.requireNonNull(binding.name.getEditText())
        .setText(getPrefManager().getString(KEY_CUSTOM_GRADLE_INSTALLATION, ""));
    builder
        .setTitle(R.string.idepref_title_customGradleInstallation)
        .setView(binding.getRoot())
        .setPositiveButton(
            android.R.string.ok,
            (dialogInterface, i) -> {
              final var path =
                  Objects.requireNonNull(binding.name.getEditText()).getText().toString().trim();
              getPrefManager().putString(KEY_CUSTOM_GRADLE_INSTALLATION, path);
            })
        .setNegativeButton(
            android.R.string.cancel, (dialogInterface, i) -> dialogInterface.dismiss())
        .show();
  }

  private void showGradleCommandsDialog() {
    final String[] labels = {
      "--stacktrace",
      "--info",
      "--debug",
      "--scan",
      "--warning-mode all",
      "--build-cache",
      "--offline"
    };
    final boolean[] checked = {
      getPrefManager().isStackTraceEnabled(),
      getPrefManager().isGradleInfoEnabled(),
      getPrefManager().isGradleDebugEnabled(),
      getPrefManager().isGradleScanEnabled(),
      getPrefManager().isGradleWarningEnabled(),
      getPrefManager().isGradleBuildCacheEnabled(),
      getPrefManager().isGradleOfflineModeEnabled()
    };
    final MaterialAlertDialogBuilder builder = DialogUtils.newMaterialDialogBuilder(getContext());
    builder.setTitle(R.string.idepref_build_customgradlecommands_title);
    builder.setMultiChoiceItems(
        labels,
        checked,
        (p1, p2, p3) -> {
          if (p2 == 0) {
            getPrefManager().setGradleStacktraceEnabled(p3);
          } else if (p2 == 1) {
            getPrefManager().setGradleInfoEnabled(p3);
          } else if (p2 == 2) {
            getPrefManager().setGradleDebugEnabled(p3);
          } else if (p2 == 3) {
            getPrefManager().setGradleScanEnabled(p3);
          } else if (p2 == 4) {
            getPrefManager().setGradleWarningEnabled(p3);
          } else if (p2 == 5) {
            getPrefManager().setGradleBuildCacheEnabled(p3);
          } else if (p2 == 6) {
            getPrefManager().setGradleOfflineModeEnabled(p3);
          }
        });
    builder.setPositiveButton(android.R.string.ok, null);
    builder.setCancelable(false);
    builder.create().show();
  }

  private void showClearCacheDialog() {
    final MaterialAlertDialogBuilder builder = DialogUtils.newMaterialDialogBuilder(getContext());
    builder.setTitle(R.string.idepref_build_clearCache_title);
    builder.setMessage(R.string.msg_clear_cache);
    builder.setCancelable(false);
    builder.setPositiveButton(
        R.string.yes,
        (p1, p2) -> {
          p1.dismiss();
          getProgressSheet().show(getChildFragmentManager(), "progress_sheet");
          new TaskExecutor().executeAsync(this::deleteCaches, __ -> getProgressSheet().dismiss());
        });
    builder.setNegativeButton(R.string.no, null);
    builder.create().show();
  }

  private Object deleteCaches() {
    File file = new File(Environment.GRADLE_USER_HOME, "caches");
    if (file.exists()) {
      FileUtils.delete(file);
    }
    return null;
  }

  private ProgressSheet getProgressSheet() {
    return progressSheet == null
        ? progressSheet = new ProgressSheet().setMessage(getString(R.string.please_wait))
        : progressSheet;
  }
}
