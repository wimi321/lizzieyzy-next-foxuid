package featurecat.lizzie.analysis;

import featurecat.lizzie.gui.Message;
import featurecat.lizzie.util.CommandLaunchHelper;
import featurecat.lizzie.util.Utils;
import java.util.List;

public class FastLink {

  public void startProgram(String command) {
    CommandLaunchHelper.LaunchSpec launchSpec =
        CommandLaunchHelper.prepare(Utils.splitCommand(command));
    List<String> commands = launchSpec.getCommandParts();
    ProcessBuilder processBuilder = new ProcessBuilder(commands);
    CommandLaunchHelper.configureProcessBuilder(processBuilder, launchSpec);
    try {
      Process process = processBuilder.start();
    } catch (Exception e) {
      Message msg = new Message();
      msg.setMessage("运行失败! ");
      // msg.setVisible(true);
    }
  }
}
