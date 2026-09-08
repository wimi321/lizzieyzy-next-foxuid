package featurecat.lizzie.util;

import featurecat.lizzie.analysis.Leelaz;
import featurecat.lizzie.analysis.remote.RemoteComputeConfig;
import java.util.List;
import java.util.Locale;

/** Transport-derived ownership of client-generated KataGo search-thread settings. */
public final class EngineThreadPolicy {
  private EngineThreadPolicy() {}

  public static boolean isRemoteManaged(String command, boolean useJavaSsh) {
    command = decodedCommand(command);
    return useJavaSsh
        || RemoteComputeConfig.isRemoteComputeEngineCommand(command)
        || isExternalSshCommand(command);
  }

  public static boolean isRemoteManaged(Leelaz engine) {
    return engine != null
        && (engine.useRemoteCompute || isRemoteManaged(engine.engineCommand(), engine.useJavaSSH));
  }

  public static boolean isRemoteManaged(List<String> command) {
    return command != null
        && !command.isEmpty()
        && (RemoteComputeConfig.isRemoteComputeEngineCommand(command.get(0))
            || isExternalSshExecutable(command.get(0)));
  }

  public static boolean isExternalSshCommand(String command) {
    List<String> tokens = Utils.splitCommand(decodedCommand(command));
    return tokens != null && !tokens.isEmpty() && isExternalSshExecutable(tokens.get(0));
  }

  private static boolean isExternalSshExecutable(String token) {
    String executable = token.replace('\\', '/');
    executable = executable.substring(executable.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
    return executable.equals("ssh")
        || executable.equals("ssh.exe")
        || executable.equals("plink")
        || executable.equals("plink.exe");
  }

  public static boolean isLocalKataGoCommand(String command, boolean useJavaSsh) {
    if (isRemoteManaged(command, useJavaSsh)) {
      return false;
    }
    List<String> tokens = Utils.splitCommand(command == null ? "" : command);
    return tokens != null
        && !tokens.isEmpty()
        && !tokens.get(0).contains("://")
        && KataGoAutoSetupHelper.looksLikeKataGoExecutable(tokens.get(0));
  }

  private static String decodedCommand(String command) {
    if (command == null) {
      return "";
    }
    return command.startsWith("encryption||") ? Utils.doDecrypt2(command.substring(12)) : command;
  }
}
