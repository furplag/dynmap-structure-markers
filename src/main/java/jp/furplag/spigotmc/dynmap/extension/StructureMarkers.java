package jp.furplag.spigotmc.dynmap.extension;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Registry;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;

import jp.furplag.sandbox.reflect.SavageReflection;
import jp.furplag.sandbox.trebuchet.Trebuchet;
import jp.furplag.spigotmc.dynmap.extension.config.Config.StructureMarkersConfig;
import jp.furplag.spigotmc.dynmap.extension.config.Config.StructureMarkersConfig.MarkerIconConfig;
import jp.furplag.spigotmc.dynmap.extension.config.Config.StructureMarkersConfig.MarkerSetConfig;
import lombok.Getter;

/**
 * Spigot plugin to generate structure markers into Dynmap automatically
 *
 * @author furplag
 *
 */
public class StructureMarkers extends JavaPlugin implements Listener {

  private final Set<Location> marked = new HashSet<>();

  private StructureMarkersConfig config;

  @SuppressWarnings({ "unused" })
  private static interface _Log {

    boolean isDebugEnabled();

    /** just a wrapper {@code JavaPlugin#getLogger()} . */
    Logger getLogger_();

    /* @formatter:off */
    default void config(final String templatedMessage, final Object... args) { getLogger_().config(format(templatedMessage, args)); }
    default void debug(final String templatedMessage, final Object... args) { if (isDebugEnabled()) { getLogger_().info(format("[DEBUG] {}", format(templatedMessage, args))); } }
    default void fine(final String templatedMessage, final Object... args) { getLogger_().fine(format(templatedMessage, args)); }
    default void finer(final String templatedMessage, final Object... args) { getLogger_().finer(format(templatedMessage, args)); }
    default void finest(final String templatedMessage, final Object... args) { getLogger_().finest(format(templatedMessage, args)); }
    default void info(final String templatedMessage, final Object... args) { getLogger_().info(format(templatedMessage, args)); }
    default void severe(final String templatedMessage, final Object... args) { getLogger_().severe(format(templatedMessage, args)); }
    default void warning(final String templatedMessage, final Object... args) { getLogger_().warning(format(templatedMessage, args)); }
    /* @formatter:on */

    private static String format(final String templatedMessage, final Object... args) {
      return StringUtils.defaultIfBlank(templatedMessage, "").replaceAll("\\{\\}", "%s").formatted(Arrays.stream(Optional.ofNullable(args).orElse(new Object[] { "" })).map((arg) -> Objects.toString(arg, "")).toArray());
    }
  }
  private final _Log log = new _Log() { @Getter private final boolean debugEnabled = Trebuchet.Predicates.orNot(config, StructureMarkersConfig::isDebugEnabled); @Getter private final Logger logger_ = getLogger(); };

  private boolean loadConfig() {
    return Trebuchet.Predicates.orElse(this, (nope) -> {
      ConfigurationSerialization.registerClass(StructureMarkersConfig.class);

      saveDefaultConfig();
      getConfig().options().copyDefaults(true);
      config = StructureMarkersConfig.deserialize(getConfig());

      if (config.isDebugEnabled()) {
        getLogger().setLevel(java.util.logging.Level.ALL);
        SavageReflection.set(log, "debugEnabled", true);
        config.serialize().entrySet().stream().forEach((o) -> log.debug("{}", o));
      }

      return Trebuchet.Predicates.orNot(config, (_config) -> _config.isEnabled());
    }, (t, ex) -> {
      ex.printStackTrace();

      return false;
    });
  }

  /**
   * register ( or update ) marker icon(s) .
   *
   * @param dynmap {@link DynmapCommonAPI}
   * @return the result of registration
   */
  private boolean registerMarkerIcons(final DynmapCommonAPI dynmap) {
    return Trebuchet.Predicates.orNot(dynmap, (_dynmap) -> {
      final MarkerAPI markerAPI = dynmap.getMarkerAPI();

      config.getMarkerIcons().stream().sorted(Comparator.comparing(MarkerIconConfig::getId)).forEach((icon) -> {
        Optional.ofNullable(markerAPI.getMarkerIcon(config.getMarkerIconId(icon))).ifPresentOrElse((markerIcon) -> {
          markerIcon.setMarkerIconLabel(icon.getLabel());
          markerIcon.setMarkerIconImage(icon.getIconImage());
        }, () -> markerAPI.createMarkerIcon(config.getMarkerIconId(icon), icon.getLabel(), icon.getIconImage()));
      });

      return config.getMarkerIcons().size() == config.getMarkerIcons().parallelStream().map(config::getMarkerIconId).map(markerAPI::getMarkerIcon).filter(Objects::nonNull).count();
    });
  }

  /**
   * register ( or update ) marker set(s) .
   *
   * @param dynmap {@link DynmapCommonAPI}
   * @return the result of registration
   */
  private boolean registerMarkerSets(final DynmapCommonAPI dynmap) {
    return Trebuchet.Predicates.orNot(dynmap, (_dynmap) -> {
      final MarkerAPI markerAPI = dynmap.getMarkerAPI();

      config.getMarkerSets().stream().sorted(Comparator.comparing(MarkerSetConfig::getPriority)).forEach((markerSetConfig) -> {
        final MarkerSet markerSet = Optional.ofNullable(markerAPI.getMarkerSet(config.getMarkerSetId(markerSetConfig))).orElse(markerAPI.createMarkerSet(config.getMarkerSetId(markerSetConfig), markerSetConfig.getLabel(), null, true));
        markerSet.setMarkerSetLabel(markerSetConfig.getLabel());
        markerSet.setHideByDefault(markerSetConfig.isHideByDefault());
        markerSet.setLayerPriority(markerSetConfig.getPriority());
        markerSet.setMinZoom(markerSetConfig.getMinZoom());
        markerSet.setMaxZoom(markerSetConfig.getMaxZoom());
        markerSet.setLabelShow(markerSetConfig.isLabelShow());
        Optional.ofNullable(Optional.ofNullable(markerAPI.getMarkerIcon(markerSetConfig.getIcon())).orElse(markerAPI.getMarkerIcon(config.getMarkerIconId(config.getMarkerIcon(markerSetConfig.getIcon())))))
          .ifPresent(markerSet::setDefaultMarkerIcon);
      });

      return config.getMarkerSets().size() == config.getMarkerSets().parallelStream().map(config::getMarkerSetId).map(markerAPI::getMarkerSet).filter(Objects::nonNull).count();
    });
  }

  private void registerMarker(final ChunkLoadEvent event) {
    Trebuchet.Consumers.orNot(event, (_event) -> {
      Optional.ofNullable(Bukkit.getPluginManager().isPluginEnabled("dynmap") ? Bukkit.getPluginManager().getPlugin("dynmap") : null).ifPresent((plugin) -> {
        final MarkerAPI markerAPI = ((DynmapCommonAPI) plugin).getMarkerAPI();
        final Chunk chunk = event.getChunk();
        final Location location = new Location(chunk.getWorld(), chunk.getX() << 4, 64, chunk.getZ() << 4);
        Optional.ofNullable(location.getWorld()).ifPresent((_world) -> Optional.ofNullable(_world.getBiome(location)).ifPresent((_biome) -> {
          Registry.STRUCTURE.stream().map((structure) -> _world.locateNearestStructure(location, structure, 1, false)).filter(Objects::nonNull)
          .filter((structureSearchResult) -> !marked.contains(structureSearchResult.getLocation()))
          .forEach((structureSearchResult) -> {
            final Location resultLocation = structureSearchResult.getLocation();
            marked.add(resultLocation);
            final MarkerIconConfig icon = config.getMarkerIcon(structureSearchResult.getStructure().getKeyOrThrow().getKey());
            config.getMarkerSets().stream().filter((layer) -> layer.isRender(structureSearchResult.getStructure())).forEach((markerSetConfig) -> {
              final Set<String> unavailableIcons = new HashSet<>();
              final Set<String> unavailableLayers = new HashSet<>();
              Optional.ofNullable(markerAPI.getMarkerSet(config.getMarkerSetId(markerSetConfig))).ifPresentOrElse((markerSet) -> {
                Optional.ofNullable(markerAPI.getMarkerIcon(config.getMarkerIconId(icon))).ifPresentOrElse((markerIcon) -> {
                  markerSet.createMarker(config.getMarkerId(markerSetConfig.getMarkerId(icon, resultLocation)), markerSetConfig.getMarkerLabel(icon, resultLocation), _world.getName(), resultLocation.getBlockX(), 64, resultLocation.getBlockZ(), markerIcon, true);
                  log.debug("add structure marker \"{}\" into marker set \"{}\" ( world: {} ) .", markerSetConfig.getMarkerLabel(icon, resultLocation), markerSetConfig.getLabel(), _world.getName());
                }, () -> { if (!unavailableIcons.contains(icon.getId())) { unavailableIcons.add(icon.getId()); log.debug("marker icon \"{}\" unavailable .", config.getMarkerIconId(icon)); }});
              }, () -> { if (!unavailableLayers.contains(markerSetConfig.getId())) { unavailableLayers.add(markerSetConfig.getId()); log.debug("marker set \"{}\" unavailable .", config.getMarkerSetId(markerSetConfig)); }});
            });
          });
        }));
      });
    });
  }

  /**
   * remove marker icon(s) registered by this plugin .
   *
   * @param dynmap {@link DynmapCommonAPI}
   * @return false if error(s) occured
   */
  @SuppressWarnings({ "unused" })
  private boolean removeMarkerIcons(final DynmapCommonAPI dynmap) {
    return Trebuchet.Predicates.orNot(dynmap, (_dynmap) -> {
      dynmap.getMarkerAPI().getMarkerIcons().parallelStream().filter((markerIcon) -> markerIcon.getMarkerIconID().startsWith("%s.".formatted(config.getMarkerIconPrefix())))
        .peek((markerIcon) -> log.debug("removing marker icon: \"{}\" .", markerIcon.getMarkerIconID())).forEach(MarkerIcon::deleteIcon);

      return true;
    });
  }

  /**
   * remove marker set(s) registered by this plugin .
   *
   * @param dynmap {@link DynmapCommonAPI}
   * @return false if error(s) occured
   */
  @SuppressWarnings({ "unused" })
  private boolean removeMarkerSets(final DynmapCommonAPI dynmap) {
    return Trebuchet.Predicates.orNot(dynmap, (_dynmap) -> {
      dynmap.getMarkerAPI().getMarkerSets().parallelStream().filter((markerSet) -> markerSet.getMarkerSetID().startsWith("%s.".formatted(config.getMarkerSetPrefix())))
        .peek((markerSet) -> log.debug("removing marker set: \"{}\" .", markerSet.getMarkerSetID())).forEach(MarkerSet::deleteMarkerSet);

      return true;
    });
  }

  /** {@inheritDoc} */ @Override public void onEnable() {
    Optional.ofNullable(loadConfig() && Bukkit.getPluginManager().isPluginEnabled("dynmap") ? Bukkit.getPluginManager().getPlugin("dynmap") : null).ifPresentOrElse((plugin) -> Trebuchet.Consumers.orElse((DynmapCommonAPI) plugin, (dynmap) -> {
      getServer().getPluginManager().registerEvents(this, this);
      if (registerMarkerIcons(dynmap)) {
        log.debug("register %d marker icon(s): %s".formatted(config.getMarkerIcons().size(), config.getMarkerIcons().stream().map(config::getMarkerIconId).sorted(Comparator.naturalOrder()).collect(Collectors.joining(",\n  ", "[\n  ", "\n]"))));
      } else {
        Trebuchet.sneakyThrow(new AssertionError("failure to register marker icon(s) ."));
      }
      if (registerMarkerSets(dynmap)) {
        log.debug("register %d marker set(s): %s".formatted(config.getMarkerSets().size(), config.getMarkerSets().stream().map(config::getMarkerSetId).sorted(Comparator.naturalOrder()).collect(Collectors.joining(",\n  ", "[\n  ", "\n]"))));
      } else {
        Trebuchet.sneakyThrow(new AssertionError("failure to register marker set(s) ."));
      }
    }, (_plugin, ex) -> {
      log.warning(ex.getMessage());
      ex.printStackTrace();
      getServer().getPluginManager().disablePlugin(this);
    }), () -> {
      // missing Dynmap plugin or disabled .
      Bukkit.getServer().getConsoleSender().sendMessage("%sDynmap plugin unavailable .".formatted(ChatColor.RED.toString()));
      getServer().getPluginManager().disablePlugin(this);
    });
  }

  @EventHandler public void onChunkLoad(ChunkLoadEvent event) {
    Bukkit.getScheduler().runTask(this, new Runnable() { @Override public void run() {
      if (config.isRender(event.getWorld())) { registerMarker(event); }
    }});
  }
}
