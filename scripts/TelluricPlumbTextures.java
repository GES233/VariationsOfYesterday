/// Generate 16x16 pixel-art block textures for the `telluric_plumb` block
/// (Telluric Plumb Frame / 地脉验山垂架).
///
/// Regenerate:  java scripts/TelluricPlumbTextures.java   (from the repo root)
/// Output:      src/main/resources/assets/variations-of-yesterday/textures/block/plumb_*.png

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class TelluricPlumbTextures {

    private static final String OUT_DIR =
            "src/main/resources/assets/variations-of-yesterday/textures/block";

    public static void main(String[] args) throws Exception {
        new File(OUT_DIR).mkdirs();
        writePng("plumb_base.png", makeBaseTexture());
        writePng("plumb_frame.png", makeFrameTexture());
        writePng("plumb_metal.png", makeMetalTexture());
        writePng("plumb_box_resting.png", makeBoxTexture(0xFF3E7A4E, 0xFF72C984, -1));
        writePng("plumb_box_refractory.png", makeBoxTexture(0xFF28899E, 0xFF6CF0FF, -2));
        writePng("plumb_box_vulnerable.png", makeBoxTexture(0xFFB86A1B, 0xFFFFB84D, 1));
        writePng("plumb_box_excitation.png", makeBoxTexture(0xFFA82020, 0xFFFF4848, 2));
        System.out.println("Generated telluric_plumb textures in " + OUT_DIR);
    }

    private static void writePng(String name, BufferedImage img) throws Exception {
        File f = new File(OUT_DIR, name);
        ImageIO.write(img, "png", f);
    }

    private static int hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1442695041;
        h = (h ^ (h >> 13)) * 1274126177;
        return (h ^ (h >> 16)) & 0xFF;
    }

    /** Weathered bluestone + cast-iron leveling plate with central crosshair dial. */
    private static BufferedImage makeBaseTexture() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int n = (hash(x, y, 11) % 13) - 6;
                boolean border = x == 0 || y == 0 || x == 15 || y == 15;
                boolean innerRim = x == 2 || y == 2 || x == 13 || y == 13;
                boolean cornerBolt = (x == 1 || x == 14) && (y == 1 || y == 14);
                int r = 64 + n, g = 67 + n, b = 70 + n;

                if (border) {
                    r = 44 + n / 2; g = 46 + n / 2; b = 50 + n / 2;
                } else if (cornerBolt) {
                    r = 168; g = 142; b = 78; // brass leveling screw
                } else if (innerRim) {
                    r = 52; g = 55; b = 58;
                } else if (x >= 4 && x <= 11 && y >= 4 && y <= 11) {
                    // Recessed dark slate dial with brass crosshair & concentric ring
                    double dx = x - 7.5, dy = y - 7.5;
                    double dist = Math.hypot(dx, dy);
                    boolean cross = (x == 7 || x == 8) || (y == 7 || y == 8);
                    boolean ring = dist >= 2.8 && dist <= 3.7;
                    if (ring) {
                        r = 145; g = 126; b = 74;
                    } else if (cross) {
                        r = 122; g = 108; b = 68;
                    } else {
                        r = 38 + n / 3; g = 41 + n / 3; b = 45 + n / 3;
                    }
                }
                img.setRGB(x, y, rgb(r, g, b));
            }
        }
        return img;
    }

    /** Aged dark-pine survey timber with iron reinforcement bands. */
    private static BufferedImage makeFrameTexture() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int grain = ((hash(x, y / 2, 23) % 11) - 5) + ((x % 4 == 0) ? -6 : 0);
                boolean ironBand = (y == 2 || y == 13);
                boolean rivet = ironBand && (x == 3 || x == 11);
                int r, g, b;
                if (rivet) {
                    r = 156; g = 136; b = 82;
                } else if (ironBand) {
                    r = 58 + grain / 2; g = 60 + grain / 2; b = 64 + grain / 2;
                } else {
                    r = 86 + grain; g = 62 + grain; b = 42 + grain;
                    if (x == 0 || x == 15) {
                        r -= 12; g -= 10; b -= 8;
                    }
                }
                img.setRGB(x, y, rgb(r, g, b));
            }
        }
        return img;
    }

    /** Multi-zone metal texture: bronze gong (top-left), cast-iron bob (top-right), brass/steel hardware (bottom). */
    private static BufferedImage makeMetalTexture() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int n = (hash(x, y, 37) % 11) - 5;
                int r, g, b;
                if (y < 8 && x < 8) {
                    // Bronze calibration gong with hammered concentric sheen
                    double dist = Math.hypot(x - 3.5, y - 3.5);
                    if (dist < 1.2) {
                        r = 212; g = 176; b = 88;
                    } else if (dist > 2.8 && dist < 3.8) {
                        r = 160 + n; g = 124 + n; b = 54 + n;
                    } else {
                        r = 188 + n; g = 148 + n; b = 68 + n;
                    }
                } else if (y < 8) {
                    // Heavy cast-iron plumb weight with specular edge
                    boolean highlight = (x == 10 || y == 2);
                    r = (highlight ? 96 : 54) + n;
                    g = (highlight ? 100 : 57) + n;
                    b = (highlight ? 108 : 63) + n;
                } else {
                    // Tarnished brass wire & spring-copper reed
                    boolean copper = (y >= 12);
                    r = (copper ? 184 : 168) + n;
                    g = (copper ? 112 : 146) + n;
                    b = (copper ? 66 : 78) + n;
                }
                img.setRGB(x, y, rgb(r, g, b));
            }
        }
        return img;
    }

    /** 1980s olive-drab enamel acoustic monitor box with paper gauge, speaker slits, and indicator lamp. */
    private static BufferedImage makeBoxTexture(int lampRimRgb, int lampCoreRgb, int needleTilt) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int n = (hash(x, y, 53) % 9) - 4;
                boolean edge = (x == 0 || y == 0 || x == 15 || y == 15);
                // Base 1980s military/geological olive-drab enamel
                int r = 68 + n, g = 82 + n, b = 58 + n;
                if (edge) {
                    r = 48 + n; g = 56 + n; b = 44 + n;
                }
                // Chipped enamel corners exposing dark steel
                if ((x == 1 && y <= 2) || (x == 14 && y >= 13)) {
                    r = 52; g = 54; b = 56;
                }
                // Cream-yellow paper gauge window [2..9, 2..7]
                if (x >= 2 && x <= 9 && y >= 2 && y <= 7) {
                    if (x == 2 || x == 9 || y == 2 || y == 7) {
                        r = 42; g = 46; b = 40; // bezel
                    } else {
                        r = 224 + n; g = 214 + n; b = 182 + n; // aged paper
                        // Scale markings at y == 3
                        if (y == 3 && (x == 4 || x == 6 || x == 8)) {
                            r = 90; g = 80; b = 70;
                        }
                        // Needle from bottom center (5, 6)
                        int nx = 5 + ((6 - y) * needleTilt) / 2;
                        if (x == nx && y >= 3 && y <= 6) {
                            r = 180; g = 36; b = 32;
                        }
                    }
                }
                // Louvered tongue-reed speaker grill [2..9, 10..13]
                if (x >= 2 && x <= 9 && y >= 10 && y <= 13) {
                    if (y % 2 == 0 && x >= 3 && x <= 8) {
                        r = 32; g = 36; b = 30;
                    } else {
                        r = 58 + n; g = 70 + n; b = 50 + n;
                    }
                }
                // Bakelite rotary knob [11..13, 10..12]
                if (x >= 11 && x <= 13 && y >= 10 && y <= 12) {
                    r = 46; g = 34; b = 30;
                    if (x == 12 && y == 10) {
                        r = 210; g = 200; b = 180; // pointer notch
                    }
                }
                img.setRGB(x, y, rgb(r, g, b));
            }
        }
        // Phase indicator lamp [11..13, 3..5]
        for (int y = 3; y <= 5; y++) {
            for (int x = 11; x <= 13; x++) {
                boolean core = (x == 12 && y == 4);
                img.setRGB(x, y, core ? lampCoreRgb : lampRimRgb);
            }
        }
        return img;
    }

    private static int rgb(int r, int g, int b) {
        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
