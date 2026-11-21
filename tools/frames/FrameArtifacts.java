import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import javax.imageio.ImageIO;

class FrameArtifacts {
    static final Pattern record = Pattern.compile("LOTTORI_(FRAME|CAPTURE) (?:(android|ios|jvm) )?([a-z0-9/-]+) ([0-9a-f:,]+) END_FRAME");

    static final Pattern part = Pattern.compile("LOTTORI_PART (android|ios|jvm) ([a-z0-9/-]+) (\\d+) (\\d+) ([0-9a-f:,]+) END_PART");

    static String joinCaptures(String source) {
        var captures = new HashMap<String,String[]>();
        var parts = part.matcher(source);
        while (parts.find()) {
            String key = parts.group(1) + " " + parts.group(2);
            String[] chunks = captures.computeIfAbsent(key, k -> new String[Integer.parseInt(parts.group(4))]);
            chunks[Integer.parseInt(parts.group(3))] = parts.group(5);
        }
        var result = new StringBuilder(source);
        captures.forEach((key, chunks) -> {
            if (!Arrays.asList(chunks).contains(null)) result.append("\nLOTTORI_CAPTURE ").append(key)
                .append(' ').append(String.join("", chunks)).append(" END_FRAME");
        });
        return result.toString();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("FrameArtifacts OUTPUT REPORT_DIRECTORY_OR_LOG...");
        Path out = Path.of(args[0]);
        for (int arg = 1; arg < args.length; arg++) {
            Path input = Path.of(args[arg]);
            if (!Files.exists(input)) continue;
            try (var paths = Files.walk(input)) {
                for (Path file : paths.filter(Files::isRegularFile).toList()) {
                    String name = file.toString();
                    if (!(name.endsWith(".xml") || name.endsWith(".log") || name.endsWith(".txt"))) continue;
                    var match = record.matcher(joinCaptures(Files.readString(file)));
                    while (match.find()) {
                        String platform = match.group(2);
                        String key = match.group(3);
                        Path png = Path.of("fixtures/frames").resolve(key + ".png");
                        BufferedImage expected = ImageIO.read(png.toFile());
                        int w = expected.getWidth(), h = expected.getHeight();
                        var actual = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                        var diff = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                        int offset = 0, worst = 0, interior = 0, significant = 0;
                        for (String run : match.group(4).split(",")) {
                            String[] parts = run.split(":");
                            int count = Integer.parseInt(parts[0]);
                            int pixel = Integer.parseUnsignedInt(parts[1], 16);
                            for (int i = 0; i < count; i++, offset++) {
                                int x = offset % w, y = offset / w;
                                actual.setRGB(x, y, pixel);
                                int ref = expected.getRGB(x, y), a = pixel >>> 24, b = ref >>> 24;
                                int error = Math.abs(a - b);
                                for (int shift : new int[]{0, 8, 16}) {
                                    int numerator = ((pixel >>> shift) & 255) * a - ((ref >>> shift) & 255) * b;
                                    error = Math.max(error, (Math.abs(numerator) + 127) / 255);
                                }
                                worst = Math.max(worst, error);
                                boolean edge = false;
                                for (int dy = Math.max(0,y-1); dy <= Math.min(h-1,y+1); dy++)
                                    for (int dx = Math.max(0,x-1); dx <= Math.min(w-1,x+1); dx++)
                                        if (expected.getRGB(dx,dy) != ref) edge = true;
                                if (!edge) interior = Math.max(interior,error);
                                if (error > 16) significant++;
                                diff.setRGB(x, y, 0xff000000 | (error << 16));
                            }
                        }
                        if (offset != w * h) throw new IOException("Incomplete frame " + key);
                        Path dir = out.resolve(platform == null ? "mismatch" : platform).resolve(key);
                        Files.createDirectories(dir);
                        ImageIO.write(expected, "png", dir.resolve("expected.png").toFile());
                        ImageIO.write(actual, "png", dir.resolve("actual.png").toFile());
                        ImageIO.write(diff, "png", dir.resolve("diff.png").toFile());
                        System.out.printf("%s max=%d interior=%d pixelsOver16=%d%n",dir,worst,interior,significant);
                    }
                }
            }
        }
    }
}
