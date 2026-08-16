package com.glyph.core.smp.imagemap;

import com.glyph.core.scheduler.SchedulerAdapter;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import javax.imageio.ImageIO;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

/**
 * {@code /mapimage <https url>} — paints a 128×128 map from an image. HTTPS only.
 */
public final class ImageMapService {

    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final SchedulerAdapter scheduler;

    public ImageMapService(SchedulerAdapter scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public void create(Player player, String rawUrl) {
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("Not a valid URL.", NamedTextColor.RED));
            return;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            player.sendMessage(Component.text("HTTPS URLs only.", NamedTextColor.RED));
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() != Material.MAP && held.getType() != Material.FILLED_MAP) {
            player.sendMessage(Component.text("Hold an empty map (or a filled map to overwrite).",
                    NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("Downloading image...", NamedTextColor.GRAY));
        scheduler.runAsync(() -> {
            try {
                BufferedImage source = download(uri);
                BufferedImage scaled = scale(source);
                scheduler.runForEntity(player, () -> apply(player, scaled), null);
            } catch (Exception e) {
                scheduler.runForEntity(player, () -> player.sendMessage(Component.text(
                        "Could not load that image.", NamedTextColor.RED)), null);
            }
        });
    }

    private void apply(Player player, BufferedImage image) {
        if (player.getWorld() == null) {
            return;
        }
        MapView view = Bukkit.createMap(player.getWorld());
        view.getRenderers().forEach(view::removeRenderer);
        view.addRenderer(new StillRenderer(image));
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        ItemStack map = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) map.getItemMeta();
        meta.setMapView(view);
        meta.displayName(Component.text("Image map", NamedTextColor.AQUA));
        map.setItemMeta(meta);
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getAmount() > 1) {
            held.setAmount(held.getAmount() - 1);
            player.getInventory().addItem(map);
        } else {
            player.getInventory().setItemInMainHand(map);
        }
        player.sendMessage(Component.text("Map painted. Frame it if you like.", NamedTextColor.GREEN));
    }

    private static BufferedImage download(URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "GlyphSMP/mapimage")
                .GET()
                .build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        long length = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        if (length > MAX_BYTES) {
            throw new IllegalStateException("too large");
        }
        try (InputStream body = response.body()) {
            BufferedImage image = ImageIO.read(body);
            if (image == null) {
                throw new IllegalStateException("not an image");
            }
            if (image.getWidth() > 4096 || image.getHeight() > 4096) {
                throw new IllegalStateException("too many pixels");
            }
            return image;
        }
    }

    private static BufferedImage scale(BufferedImage source) {
        BufferedImage scaled = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, 128, 128, null);
        graphics.dispose();
        return scaled;
    }

    private static final class StillRenderer extends MapRenderer {
        private final BufferedImage image;
        private boolean drawn;

        private StillRenderer(BufferedImage image) {
            super(true);
            this.image = image;
        }

        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            if (drawn) {
                return;
            }
            canvas.drawImage(0, 0, image);
            drawn = true;
        }
    }
}
