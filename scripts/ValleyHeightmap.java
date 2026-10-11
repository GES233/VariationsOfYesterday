/// Generate the placeholder heightmap for the demo_valley world type.
///
/// One pixel = one block column, 512x512 (32x32 chunks, matching the mandate
/// lattice extent), pixel (0,0) = block (-256, -256). Gray 0..255 maps to
/// surface y in [48, 144] (see DemoValleyChunkGenerator.BaseHeight/TopHeight).
///
/// Layout (design-draft demo staging):
///   - main valley meandering north-south, floor y~58 (below sea level 63, so
///     a stream forms along the thalweg)
///   - one side gully joining from the east (upstream "engineering site" slot)
///   - flat dry basin (y~66) around the origin for the stockade / spawn area
///   - ridges ~130; outside the region the generator blends to plain y=80
///
/// Written in Java (single-file source) on purpose: the mod reads the PNG via
/// ImageIO, so the same library writes it — a hand-rolled Python PNG encoder
/// produced a file ImageIO decoded differently (center pixel 120 vs 48).
///
/// Regenerate:  java scripts/ValleyHeightmap.java   (from the repo root)
/// Output:      src/main/resources/data/variations-of-yesterday/valley/heightmap.png

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class ValleyHeightmap {

    private static final int SIZE = 512;
    private static final int HALF = SIZE / 2;
    private static final double BASE_Y = 48, TOP_Y = 144;

    public static void main(String[] args) throws Exception {
        var out = "src/main/resources/data/variations-of-yesterday/valley/heightmap.png";

        var image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_BYTE_GRAY);
        for (int pz = 0; pz < SIZE; pz++) {
            for (int px = 0; px < SIZE; px++) {
                double y = height(px - HALF, pz - HALF);
                int gray = (int) Math.round((y - BASE_Y) * 255.0 / (TOP_Y - BASE_Y));
                image.getRaster().setSample(px, pz, 0, Math.max(0, Math.min(255, gray)));
            }
        }
        ImageIO.write(image, "png", new File(out));

        // Read back through the same ImageIO path the mod uses. NB: read the
        // raw raster sample — getRGB applies a gray->sRGB conversion.
        var check = ImageIO.read(new File(out));
        int center = check.getRaster().getSample(HALF, HALF, 0);
        System.out.printf("wrote %s; center gray=%d -> y=%d%n",
                out, center, 48 + (center * 96 + 127) / 255);
    }

    private static double smoothstep(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double height(double x, double z) {
        // main valley centerline (runs along z)
        double cx = 40.0 * Math.sin(z * 0.008) + 20.0 * Math.sin(z * 0.023);
        double d = Math.abs(x - cx);

        // side gully from the east, joining the main valley around z = -80
        if (x > 40.0) {
            double zg = -80.0 + 15.0 * Math.sin(x * 0.02);
            d = Math.min(d, Math.abs(z - zg) + Math.max(0.0, 60.0 - x) * 2.0);
        }

        double y = 58.0 + 75.0 * smoothstep((d - 25.0) / 85.0);

        // flatten a dry basin around the origin (stockade / spawn)
        double w = smoothstep((60.0 - Math.hypot(x, z)) / 40.0);
        y = 66.0 + (y - 66.0) * (1.0 - w);

        // gentle deterministic roughness
        y += 6.0 * Math.sin(x * 0.031) * Math.cos(z * 0.027)
                + 4.0 * Math.sin(x * 0.05 + z * 0.041)
                + 3.0 * Math.sin(x * 0.13) * Math.sin(z * 0.11);
        return y;
    }
}
