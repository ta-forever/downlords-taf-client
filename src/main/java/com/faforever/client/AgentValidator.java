package com.faforever.client;

import javax.swing.JOptionPane;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class AgentValidator {

  private static final String KNOWN_AGENT_PREFIX_DEV = "webview-patch/build/libs/webview-patch.jar";
  private static final String KNOWN_AGENT_PREFIX_RELEASE = "lib/webview-patch.jar";

  public static void checkForConflictingJavaAgents() {
    List<String> inputArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();

    Path path = Paths.get(KNOWN_AGENT_PREFIX_RELEASE);
    Path path1 = Paths.get(KNOWN_AGENT_PREFIX_DEV);

    if (Files.exists(path1) && Files.exists(path)) {
      JOptionPane.showMessageDialog(null,
          "Mixed release/dev webview patch detected",
          "Agent Validation Error",
          JOptionPane.ERROR_MESSAGE);
      System.exit(1);
    }

    if (!Files.exists(path1) && !Files.exists(path)) {
      JOptionPane.showMessageDialog(null,
          "No webview patch detected",
          "Agent Validation Error",
          JOptionPane.ERROR_MESSAGE);
      System.exit(1);
    }

    for (String arg : inputArgs) {
      if (arg.startsWith("-javaagent:")) {
        String agentPath = arg.substring("-javaagent:".length());
        if (!agentPath.replace("\\", "/").equals(KNOWN_AGENT_PREFIX_DEV) &&
            !agentPath.replace("\\", "/").equals(KNOWN_AGENT_PREFIX_RELEASE)) {
          JOptionPane.showMessageDialog(null,
              "Unexpected javaagent detected:\n" + agentPath,
              "Agent Validation Error",
              JOptionPane.ERROR_MESSAGE);
          System.exit(1);
        }
      }
    }
  }
}
