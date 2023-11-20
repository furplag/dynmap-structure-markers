package jp.furplag.spigotmc.dynmap.extension.config;

import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.generator.structure.Structure;

import com.google.common.base.CaseFormat;

import jp.furplag.sandbox.reflect.Reflections;
import jp.furplag.sandbox.reflect.SavageReflection;
import jp.furplag.sandbox.trebuchet.Trebuchet;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * plugin configuration .
 * 
 * @author furplag
 *
 */
public interface Config extends ConfigurationSerializable {

  static final String pluginName = "dynmap-structure-markers";

  @SerializableAs("jp.furplag.spigotmc.dynmap.extension.config._Config.StructureMarkersConfig")
  @RequiredArgsConstructor
  @Getter
  @EqualsAndHashCode
  @ToString
  public static class StructureMarkersConfig implements Config {

    public static final StructureMarkersConfig deserialize(final ConfigurationSection config) {
      return new StructureMarkersConfig(true, false, "structure-marker", "structure-marker", "structure-marker") {{
        relaxingBinding(config);
        SavageReflection.set(this, "markerIcons", MarkerIconConfig.deserialize(config));
        SavageReflection.set(this, "markerSets", MarkerSetConfig.deserialize(config));
      }};
    }

    private static final String _join(final String delimiter, final Object... args) {
      return Arrays.stream(Optional.ofNullable(args).orElse(new Object[] {})).map((arg) -> Objects.toString(arg, null)).filter(Objects::nonNull).collect(Collectors.joining(StringUtils.defaultIfBlank(delimiter, "")));  
    }

    /** whether available this plugin or not ( default: true ) */
    private final boolean enabled;

    /** enable to logging more details ( default: false ) */
    private final boolean debugEnabled;

    /**
     * world name(s) that allowed marking structures into the map, 
     * empty means "All maps accepts to mark structures"
     */
    private final Set<String> worlds = new HashSet<>();

    /** used to detect the {@link org.dynmap.markers.Marker Marker} this plugin made ( default: "structure-marker" ) */
    private final String markerPrefix;

    /** used to detect the {@link org.dynmap.markers.MarkerIcon MarkerIcon} this plugin made ( default: "structure-marker" ) */
    private final String markerIconPrefix;

    /** used to detect the {@link org.dynmap.markers.MarkerSet MarkerSet} this plugin made ( default: "structure-marker" ) */
    private final String markerSetPrefix;

    /**
     * handles {@link org.dynmap.markers.MarkerIcon MarkerIcon} .
     *
     */
    @SerializableAs("jp.furplag.spigotmc.dynmap.extension.config._Config.StructureMarkersConfig.MarkerIconConfig")
    @RequiredArgsConstructor
    @Getter
    @EqualsAndHashCode
    @ToString
    public static class MarkerIconConfig implements Config {

      private static final MarkerIconConfig _defaults = new MarkerIconConfig("_unresolved", "Unknown Structure", null);

      /** identity of {@link org.dynmap.markers.MarkerIcon MarkerIcon} */
      private final @Nonnull String id;

      /**
       * <p>the name use to display marker into the map .</p> 
       * using {@link #id} formatted with typical Letter-case ( space separated ) by defaults .
       */
      private final String label;

      /**
       * enable to override icon image if you need .
       * <ol>
       * <li>put image into {@link org.bukkit.plugin.Plugin#getDataFolder() Plugin Folder} .</li>
       * <li>set the image path of under {@link org.bukkit.plugin.Plugin#getDataFolder() Plugin Folder} .</li>
       * </ol> 
       */
      private final String icon;

      private static final List<MarkerIconConfig> deserialize(final ConfigurationSection config) {
        return Stream.concat(Stream.of(_defaults), Registry.STRUCTURE.stream().flatMap((structure) -> Stream.of(structure.getKey().getKey(), structure.getStructureType().getKey().getKey())).map((id) -> id.split(":")).map((ids) -> ids[ids.length - 1])
          .distinct().map((id) -> deserialize(id, config.getConfigurationSection("marker-icons.%s".formatted(id)))).filter(Objects::nonNull)
        ).sorted(Comparator.comparing(MarkerIconConfig::getId)).collect(Collectors.toUnmodifiableList());
      }

      private static final MarkerIconConfig deserialize(final String id, final ConfigurationSection config) {
        return new MarkerIconConfig(
            Objects.requireNonNull(StringUtils.defaultIfBlank(id, null))
          , Arrays.stream(id.split("_")).map((s) -> CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, s)).filter(StringUtils::isNotBlank).collect(Collectors.joining(" ")), null) {{
          relaxingBinding(config);
        }};
      }

      /**
       * returns a image of marker icon .
       * 
       * @return a stream of image
       */
      public InputStream getIconImage() {
        return Objects.requireNonNullElse(StringUtils.isBlank(icon) ? getClass().getResourceAsStream("/icons/%s.png".formatted(id)) : Trebuchet.Functions.orNot(Bukkit.getPluginManager().getPlugin(pluginName).getDataFolder().toPath().resolve(icon), Files::newInputStream), StructureMarkersConfig.class.getResourceAsStream("/icons/_default.png".formatted(id)));
      }
    }
    private final List<MarkerIconConfig> markerIcons = new ArrayList<>();

    /**
     * handles {@link org.dynmap.markers.MarkerSet MarkerSet} .
     *
     */
    @SerializableAs("jp.furplag.spigotmc.dynmap.extension.config._Config.StructureMarkersConfig.MarkerSetConfig")
    @RequiredArgsConstructor
    @Getter
    @EqualsAndHashCode
    @ToString
    public static class MarkerSetConfig implements Config {

      /** identity of {@link org.dynmap.markers.MarkerSet MarkerSet} */
      private final @Nonnull String id;

      /**
       * <p>the name use to display marker layer toggles .</p> 
       * using {@link #id} formatted with typical Letter-case ( space separated ) by defaults .
       */
      private final String label;

      /** handles {@link org.dynmap.markers.MarkerSet#setHideByDefault(boolean) setHideByDefault(boolean)} ( default: false ) */
      private final boolean hideByDefault;

      /** handles {@link org.dynmap.markers.MarkerSet#setMinZoom(int)} ( default: -1 ) */
      private final int minZoom;

      /** handles {@link org.dynmap.markers.MarkerSet#setMaxZoom(int)} ( default: -1 ) */
      private final int maxZoom;

      /** handles {@link org.dynmap.markers.MarkerSet#setLayerPriority(int)} ( default: 10 ) */
      private final int priority;

      /** handles {@link org.dynmap.markers.MarkerSet#setLabelShow(Boolean)} ( default: false ) */
      private final boolean labelShow;

      /** handles {@link org.dynmap.markers.MarkerSet#setDefaultMarkerIcon(org.dynmap.markers.MarkerIcon)} ( default: null ) */
      private final String icon;

      /** append location "[ X, Z ]" into marker label, if true ( default: true ) */
      private final boolean labelWithCoordinates;

      /**
       * <p>keys of Structure ( and also StructureType ) to generate into this map layer .</p>
       * empty means "generate all structure markers into the marker set" .
       */
      private final Set<String> structures = new HashSet<>();

      private static final List<MarkerSetConfig> deserialize(final ConfigurationSection config) {
        
        return Trebuchet.Functions.orElse(config, (_config) -> _config.getConfigurationSection("marker-sets").getValues(false), (t, ex) -> {ex.printStackTrace(); return new HashMap<String, Object>();})
            .keySet().stream().map((k) -> Map.entry(k, config.getConfigurationSection(_join(".", "marker-sets", k)))).map((e) -> deserialize(e.getKey(), e.getValue()))
            .filter(Objects::nonNull).collect(Collectors.toUnmodifiableList());
      }

      private static final MarkerSetConfig deserialize(final String id, final ConfigurationSection config) {
        return new MarkerSetConfig(
            Objects.requireNonNull(StringUtils.defaultIfBlank(id, null))
          , Arrays.stream(id.split("_")).map((s) -> CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, s)).filter(StringUtils::isNotBlank).collect(Collectors.joining()), false, -1, -1, 10, false, null, true) {{
            relaxingBinding(config);
            Trebuchet.Consumers.orNot(config.getList("structures", Collections.emptyList()), (s) -> SavageReflection.set(this, "structures", s.stream().map((_s) -> Objects.toString(_s, "")).filter(StringUtils::isNotBlank).collect(Collectors.toUnmodifiableSet())));
        }};
      }

      public String getMarkerId(final MarkerIconConfig icon, final Location location) {
        return _join(",", Trebuchet.Functions.orElse(icon, MarkerIconConfig::getId, () -> id), location.getBlockX(), location.getBlockZ());
      }

      public String getMarkerLabel(final MarkerIconConfig icon, final Location location) {
        return "%s%s".formatted(Trebuchet.Functions.orElse(icon, MarkerIconConfig::getLabel, () -> label), labelWithCoordinates ? " [ %d, %d ]".formatted(location.getBlockX(), location.getBlockZ()) : "");
      }

      public boolean isRender(final Structure structure) {
        return structures.isEmpty() || structures.contains(structure.getKey().getKey());
      }
    }
    private final List<MarkerSetConfig> markerSets = new ArrayList<>();

    public MarkerIconConfig getMarkerIcon(final String id) {
      return markerIcons.parallelStream().filter((o) -> o.getId().equals(id)).findFirst().orElse(null);
    }

    public String getMarkerIconId(final MarkerIconConfig icon) {
      return _join(".", markerIconPrefix, Trebuchet.Functions.orNot(icon, MarkerIconConfig::getId));
    }

    public String getMarkerId(final String id) {
      return _join(".", markerPrefix, id);
    }

    public MarkerSetConfig getMarkerSet(final String id) {
      return markerSets.parallelStream().filter((o) -> o.getId().equals(id)).findFirst().orElse(null);
    }

    public String getMarkerSetId(final MarkerSetConfig layer) {
      return _join(".", markerSetPrefix, Trebuchet.Functions.orNot(layer, MarkerSetConfig::getId));
    }

    public boolean isRender(final World world) {
      return Trebuchet.Predicates.orNot(world, (_world) -> _world.canGenerateStructures() && (worlds.isEmpty() || worlds.contains(_world.getName())));
    }

    /** {@inheritDoc} */ @Override public Map<String, Object> serialize() {
      return SavageReflection.read(this);
    }
  }

  default boolean relaxingBinding(final ConfigurationSection config, final String... exculudes) {
    final Set<String> _exculudes = Arrays.stream(Optional.ofNullable(exculudes).orElse(new String[] {})).filter(StringUtils::isNotBlank).distinct().collect(Collectors.toUnmodifiableSet());

    return config != null && Arrays.stream(Reflections.getFields(this))
      .filter((f) -> !Reflections.isStatic(f)).filter((f) -> !_exculudes.contains(f.getName()))
      .map((f) -> {
        final Object newValue = config.get(f.getName(), config.get(CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, f.getName())));

        return newValue != null && Reflections.isAssignable(this, f, newValue) ? Map.entry(f, newValue) : null;
      }).filter(Objects::nonNull)
      .map((e) -> Trebuchet.Predicates.orNot(this, e.getKey(), e.getValue(), SavageReflection::set))
      .allMatch((r) -> r);
  }

  /** {@inheritDoc} */ @Override default Map<String, Object> serialize() {
    return SavageReflection.read(this);
  }
}
