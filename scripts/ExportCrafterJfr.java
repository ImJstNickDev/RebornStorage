import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.file.Path;
import java.util.Set;
import jdk.jfr.consumer.*;

/** JDK 17 diagnostic utility; never part of the mod. Keeps only fields the analyzer needs. */
class ExportCrafterJfr {
  private static String quote(String value) {
    return "\""
        + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        + "\"";
  }

  public static void main(String[] args) throws Exception {
    var types =
        Set.of(
            "jdk.ExecutionSample",
            "jdk.ObjectAllocationInNewTLAB",
            "jdk.ObjectAllocationOutsideTLAB");
    try (var recording = new RecordingFile(Path.of(args[0]));
        var out =
            new BufferedWriter(
                new OutputStreamWriter(System.out, java.nio.charset.StandardCharsets.UTF_8))) {
      out.write("{\"recording\":{\"events\":[");
      boolean first = true;
      while (recording.hasMoreEvents()) {
        var event = recording.readEvent();
        String type = event.getEventType().getName();
        if (!types.contains(type)) continue;
        RecordedThread thread =
            event.getThread(type.equals("jdk.ExecutionSample") ? "sampledThread" : "eventThread");
        if (thread == null || !thread.getJavaName().equals("Server thread")) continue;
        if (!first) out.write(",");
        first = false;
        out.write(
            "{\"type\":"
                + quote(type)
                + ",\"values\":{\"startTime\":"
                + quote(event.getStartTime().toString())
                + ",\"eventThread\":{\"javaName\":\"Server thread\"}");
        if (!type.equals("jdk.ExecutionSample")) {
          String field = type.endsWith("InNewTLAB") ? "tlabSize" : "allocationSize";
          out.write("," + quote(field) + ":" + event.getLong(field));
        }
        out.write(",\"stackTrace\":{\"frames\":[");
        boolean firstFrame = true;
        if (event.getStackTrace() != null && type.equals("jdk.ExecutionSample")) {
          for (var frame : event.getStackTrace().getFrames()) {
            if (!firstFrame) out.write(",");
            firstFrame = false;
            var method = frame.getMethod();
            out.write(
                "{\"method\":{\"type\":{\"name\":"
                    + quote(method.getType().getName())
                    + "},\"name\":"
                    + quote(method.getName())
                    + "}}");
          }
        }
        out.write("]}}}");
      }
      out.write("]}}\n");
    }
  }
}
