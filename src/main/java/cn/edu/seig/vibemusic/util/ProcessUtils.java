package cn.edu.seig.vibemusic.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * 轻量子进程执行器：同时消费标准输出与错误输出，避免阻塞
 */
public class ProcessUtils {
    private static final Logger log = LoggerFactory.getLogger(ProcessUtils.class);

    public static String execute(String cmd, boolean showLog) {
        if (cmd == null || cmd.isEmpty()) return "";
        final Process process;
        try {
            // Windows 与 *nix 兼容执行
            boolean isWin = System.getProperty("os.name").toLowerCase().contains("win");
            process = isWin ?
                    Runtime.getRuntime().exec(cmd) :
                    Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", cmd});

            StringBuilder out = new StringBuilder();
            StringBuilder err = new StringBuilder();

            Thread t1 = new Thread(() -> readStream(process.getInputStream(), out));
            Thread t2 = new Thread(() -> readStream(process.getErrorStream(), err));
            t1.start();
            t2.start();
            int code = process.waitFor();
            t1.join();
            t2.join();
            String res = out.append('\n').append(err).toString();
            if (showLog) log.info("Exec: {}\n{}", cmd, res);
            if (code != 0 && showLog) log.warn("Command exit code {}", code);
            return res;
        } catch (Exception e) {
            if (showLog) log.error("Exec failed: {}", e.getMessage());
            return "";
        } 
    }

    private static void readStream(InputStream is, StringBuilder sb) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        } catch (IOException ignored) {}
    }
}


