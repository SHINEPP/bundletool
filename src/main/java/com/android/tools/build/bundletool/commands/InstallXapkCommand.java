package com.android.tools.build.bundletool.commands;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.Feature;
import com.android.tools.build.bundletool.device.AdbRunner;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.device.Device;
import com.android.tools.build.bundletool.flags.Flag;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.io.TempDirectory;
import com.android.tools.build.bundletool.model.utils.DefaultSystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.utils.MapUtils;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.android.tools.build.bundletool.commands.CommandUtils.ANDROID_SERIAL_VARIABLE;
import static com.android.tools.build.bundletool.model.utils.SdkToolsLocator.ANDROID_HOME_VARIABLE;
import static com.android.tools.build.bundletool.model.utils.SdkToolsLocator.SYSTEM_PATH_VARIABLE;

/**
 * Installs XAPK on a connected device.
 * <p>
 * APKPure XAPK
 */

@AutoValue
public abstract class InstallXapkCommand {

  public static final String COMMAND_NAME = "install-xapk";

  private static final Flag<Path> ADB_PATH_FLAG = Flag.path("adb");
  private static final Flag<Path> XAPK_ARCHIVE_FILE_FLAG = Flag.path("xapk");
  private static final Flag<String> DEVICE_ID_FLAG = Flag.string("device-id");

  private static final SystemEnvironmentProvider DEFAULT_PROVIDER = new DefaultSystemEnvironmentProvider();

  private static final String MANIFEST_NAME = "manifest.json";

  public abstract Path getAdbPath();

  public abstract Path getXapkArchivePath();

  public abstract Optional<String> getDeviceId();

  public abstract AdbServer getAdbServer();

  public abstract boolean getAllowDowngrade();

  public abstract boolean getAllowTestOnly();

  public abstract boolean getGrantRuntimePermissions();

  public abstract Duration getTimeout();

  public static Builder builder() {
    return new AutoValue_InstallXapkCommand.Builder()
        .setAllowDowngrade(false)
        .setAllowTestOnly(false)
        .setGrantRuntimePermissions(false)
        .setTimeout(Device.DEFAULT_ADB_TIMEOUT);
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setAdbPath(Path adbPath);

    public abstract Builder setXapkArchivePath(Path apksArchivePath);

    public abstract Builder setDeviceId(String deviceId);

    public abstract Builder setAdbServer(AdbServer server);

    public abstract Builder setAllowDowngrade(boolean allowDowngrade);

    public abstract Builder setAllowTestOnly(boolean allowTestOnly);

    public abstract Builder setGrantRuntimePermissions(boolean value);

    public abstract Builder setTimeout(Duration timeout);

    public abstract InstallXapkCommand build();
  }

  public static InstallXapkCommand fromFlags(ParsedFlags flags, AdbServer adbServer) {
    return fromFlags(flags, DEFAULT_PROVIDER, adbServer);
  }

  public static InstallXapkCommand fromFlags(
      ParsedFlags flags, SystemEnvironmentProvider systemEnvironmentProvider, AdbServer adbServer) {

    Path xapkArchivePath = XAPK_ARCHIVE_FILE_FLAG.getRequiredValue(flags);
    Path adbPath = CommandUtils.getAdbPath(flags, ADB_PATH_FLAG, systemEnvironmentProvider);

    Optional<String> deviceSerialName =
        CommandUtils.getDeviceSerialName(flags, DEVICE_ID_FLAG, systemEnvironmentProvider);

    InstallXapkCommand.Builder command = builder().setAdbPath(adbPath)
        .setAdbServer(adbServer)
        .setXapkArchivePath(xapkArchivePath);
    deviceSerialName.ifPresent(command::setDeviceId);
    return command.build();
  }

  public void execute() {
    AdbServer adbServer = getAdbServer();
    adbServer.init(getAdbPath());

    try (TempDirectory tempDirectory = new TempDirectory()) {
      final ImmutableList<Path> apksToInstall = getApksToInstall(tempDirectory.getPath());

      AdbRunner adbRunner = new AdbRunner(adbServer);
      Device.InstallOptions installOptions =
          Device.InstallOptions.builder()
              .setAllowDowngrade(getAllowDowngrade())
              .setAllowTestOnly(getAllowTestOnly())
              .setGrantRuntimePermissions(getGrantRuntimePermissions())
              .setTimeout(getTimeout())
              .build();

      if (getDeviceId().isPresent()) {
        adbRunner.run(
            device -> device.installApks(apksToInstall, installOptions), getDeviceId().get());
      } else {
        adbRunner.run(device -> device.installApks(apksToInstall, installOptions));
      }
    }
  }

  private ImmutableList<Path> getApksToInstall(Path output) {
    ArrayList<Path> paths = new ArrayList<>();

    try (ZipFile xapkZip = new ZipFile(getXapkArchivePath().toFile())) {
      ZipEntry manifest = xapkZip.getEntry(MANIFEST_NAME);
      try (InputStream inputStream = xapkZip.getInputStream(manifest)) {
        HashMap<?, ?> map = JSON.parseObject(inputStream, LinkedHashMap.class, Feature.OrderedField);
        String packageName = MapUtils.optString(map, "", "package_name");
        System.out.println("Install package " + packageName);
        List<?> splitApks = MapUtils.optList(map, new ArrayList<>(), "split_apks");
        for (Object splitApk : splitApks) {
          String id = MapUtils.optString(splitApk, "", "id");
          String file = MapUtils.optString(splitApk, "", "file");
          if (!file.isEmpty()) {
            ZipEntry apk = xapkZip.getEntry(file);
            try (InputStream apkInput = xapkZip.getInputStream(apk)) {
              Path path = Paths.get(output.toString(), file);
              Files.copy(apkInput, path, StandardCopyOption.REPLACE_EXISTING);
              paths.add(path);
            }
          }
        }
      }

    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    return ImmutableList.<Path>builder().addAll(paths).build();
  }

  public static CommandHelp help() {
    return CommandHelp.builder()
        .setCommandName(COMMAND_NAME)
        .setCommandDescription(
            CommandHelp.CommandDescription.builder()
                .setShortDescription(
                    "Installs XAPK downloaded from APKPure to a connected device. "
                        + "Replaces already installed package.")
                .addAdditionalParagraph(
                    "This will extract from the APK Set archive and install only the XAPK that "
                        + "would be served to that device. If the app is not compatible with the "
                        + "device or if the APK Set archive was generated for a different type of "
                        + "device, this command will fail.")
                .build())
        .addFlag(
            CommandHelp.FlagDescription.builder()
                .setFlagName(ADB_PATH_FLAG.getName())
                .setExampleValue("path/to/adb")
                .setOptional(true)
                .setDescription(
                    "Path to the adb utility. If absent, an attempt will be made to locate it if "
                        + "the %s or %s environment variable is set.",
                    ANDROID_HOME_VARIABLE, SYSTEM_PATH_VARIABLE)
                .build())
        .addFlag(
            CommandHelp.FlagDescription.builder()
                .setFlagName(XAPK_ARCHIVE_FILE_FLAG.getName())
                .setExampleValue("archive.xapk")
                .setDescription(
                    "Path to the archive file generated by the '%s' command.",
                    BuildApksCommand.COMMAND_NAME)
                .build())
        .addFlag(
            CommandHelp.FlagDescription.builder()
                .setFlagName(DEVICE_ID_FLAG.getName())
                .setExampleValue("device-serial-name")
                .setOptional(true)
                .setDescription(
                    "Device serial name. If absent, this uses the %s environment variable. Either "
                        + "this flag or the environment variable is required when more than one "
                        + "device or emulator is connected.",
                    ANDROID_SERIAL_VARIABLE)
                .build())
        .build();
  }
}
