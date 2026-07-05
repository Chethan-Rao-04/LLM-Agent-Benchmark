package org.benchmark.gen.tool_generator;

import org.benchmark.model.enums.Domain;
import org.benchmark.model.objects.OptionEntity;

import java.util.Random;
import java.util.List;


/**
 * Domain vocabulary source for synthetic tool and command generation.
 *
 * <p>This dictionary provides verbs, nouns, state variable names, and common options
 * used by generators to create realistic but synthetic CLI specifications.</p>
 */
public class CommandDict {
    private final Random random;

    /**
     * Creates a dictionary wrapper that samples terms using the provided random source.
     *
     * @param random random source used for value selection
     */
    public CommandDict(Random random) {
        this.random = random;
    }

    private static final List<String> VERBS_MAN = List.of(
            "start", "stop", "execute", "pause", "resume", "shutdown", "restart",
            "abort", "reboot", "calibrate", "measure", "inspect", "run",
            "load", "unload", "operate", "set", "unset", "align", "bypass",
            "clamp", "disable", "enable", "initialize", "lubricate", "override",
            "package", "purge", "quality-check", "record", "seal", "toggle",
            "verify", "weld", "zero-out", "torque", "wring", "extrude", "mold"
    );

    private static final List<String> NOUNS_MAN = List.of(
            "alarm", "process", "sensor", "batch", "parts", "build", "schedule",
            "line", "material", "machine", "toolSpec", "unit", "controller",
            "actuator", "conveyor", "dye", "engine", "fixture", "gantry",
            "hopper", "inventory", "jig", "kiln", "mold", "nozzle", "operator",
            "pallet", "queue", "raw-material", "spindle", "valve", "assembly",
            "chassis", "workstation", "compressor", "generator", "turbine"
    );

    private static final List<String> STATE_VARS_MAN = List.of(
            "status", "temperature", "pressure", "speed", "voltage", "current",
            "duration", "component_id", "threshold", "count", "rate", "position",
            "level", "power", "error_count", "air_flow", "battery_level",
            "cycle_time", "density", "efficiency", "friction", "humidity",
            "inert_gas_level", "joint_angle", "load_cell", "moisture", "noise_level",
            "output_yield", "quality_score", "rotation_speed", "torque", "vibration",
            "wear_level", "lubrication_index", "coolant_temp", "psi_reading"
    );

    private static final List<String> VERBS_NET = List.of(
            "route", "switch", "bridge", "watch", "trace", "capture",
            "allow", "deny", "block", "save", "load", "update", "setup",
            "ping", "connect", "disconnect", "authenticate", "broadcast",
            "configure", "encrypt", "decrypt", "filter", "forward", "handshake",
            "isolate", "lease", "map", "optimize", "propagate", "release",
            "synchronize", "tunnel", "whitelist", "blacklist", "throttle",
            "advertise", "peer", "inspect"
    );

    private static final List<String> NOUNS_NET = List.of(
            "device", "module", "channel", "config", "interface", "network",
            "node", "router", "repeater", "location", "firewall", "packet", "port", "service",
            "access-point", "backbone", "bridge", "client", "datagram", "ethernet",
            "gateway", "host", "infrastructure", "jumper", "kernel", "load-balancer",
            "mainframe", "nat", "ontology", "proxy", "server", "topology", "endpoint",
            "vlan", "subnet", "switchport", "trunk", "uplink", "downlink"
    );

    private static final List<String> STATE_VARS_NET = List.of(
            "latency", "throughput", "status_code", "ip_address", "subnet_mask",
            "gateway", "port_no", "mac_address", "interface_name", "bandwidth",
            "packet_loss", "protocol_type", "session_id", "dns_server", "vlan_id", "route_metric",
            "uptime", "mtu", "ttl", "jitter", "signal_strength", "channel_width",
            "noise_floor", "lease_time", "hop_count", "retransmit_rate", "collision_count",
            "buffer_size", "cpu_usage", "memory_usage", "rx_bytes", "tx_bytes",
            "connection_count", "drop_rate", "latency_jitter", "power_level"
    );

    private static final List<String> COMMON_OPTS = List.of(
            "verbose", "quiet", "debug", "confirm", "force"
    );


    /**
     * Generates a tool name in the format {@code XXX-NOUN-123}.
     *
     * @param domain domain to sample noun from
     * @return generated tool name
     */
    public String generateToolName(Domain domain) {
        return domain.name().substring(0, 3).toUpperCase() + "-" +
                getRandomNoun(domain).toUpperCase() + "-" +
                (100 + random.nextInt(900));
    }


    /**
     * Returns a random command verb for the given domain.
     *
     * @param domain source domain
     * @return random verb
     */
    public String getRandomVerb(Domain domain) {
        return switch (domain) {
            case MANUFACTURING -> pickOne(VERBS_MAN);
            case NETWORK_INFRA -> pickOne(VERBS_NET);
        };
    }


    /**
     * Returns a random command noun for the given domain.
     *
     * @param domain source domain
     * @return random noun
     */
    public String getRandomNoun(Domain domain) {
        return switch (domain) {
            case MANUFACTURING -> pickOne(NOUNS_MAN);
            case NETWORK_INFRA -> pickOne(NOUNS_NET);
        };
    }


    /**
     * Returns a random common option specification.
     *
     * @return random option spec
     */
    public OptionEntity getRandomCommonOptionSpec() {
        String optionNameFromDict = pickOne(COMMON_OPTS);
        return new OptionEntity("--" + optionNameFromDict, getCommonFlagDescription(optionNameFromDict));
    }

    private static String getCommonFlagDescription(String optionNameFromDict) {
        return switch (optionNameFromDict) {
            case "verbose" -> "Execute with detailed status output.";
            case "quiet" -> "Execute with minimal status output.";
            case "debug" -> "Execute with diagnostic status output.";
            case "confirm" -> "Confirm that the command should be applied.";
            case "force" -> "Apply the command using the force execution mode.";
            default -> "Enable " + optionNameFromDict + " mode.";
        };
    }

    /**
     * Returns a random state variable name for the given domain.
     *
     * @param domain source domain
     * @return state variable name
     */
    public String getRandomStateVariable(Domain domain) {
        return switch (domain) {
            case MANUFACTURING -> pickOne(STATE_VARS_MAN);
            case NETWORK_INFRA -> pickOne(STATE_VARS_NET);
        };
    }


    private String pickOne(List<String> list) {
        return list.get(random.nextInt(list.size()));
    }
}
