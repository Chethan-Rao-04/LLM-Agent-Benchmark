package org.benchmark.gen;

import org.benchmark.model.enums.Domain;
import org.benchmark.model.spec.OptionSpec;

import java.util.Arrays;
import java.util.Random;


import java.util.List;


/**
 * A dictionary of commands terms for the industrial domains (to be re-checked)
 */



public class CommandDict {
    private final Random random;

    public CommandDict(Random random){
        this.random = random;
    }
    public static final List<Domain> DOMAINS = List.of(
           Domain.MANUFACTURING,  Domain.NETWORK_INFRA,  Domain.HEALTHCARE
    );

    // Manufacturing

    public static final List<String> VERBS_MAN = Arrays.asList(
            "start", "stop", "execute", "pause", "resume", "shutdown", "restart",
            "abort", "reboot", "calibrate", "measure", "inspect", "run",
            "load", "unload", "operate", "set", "unset", "align", "bypass",
            "clamp", "disable", "enable", "initialize", "lubricate", "override",
            "package", "purge", "quality-check", "record", "seal", "toggle",
            "verify", "weld", "zero-out", "torque", "wring", "extrude", "mold"
    );

    public static final List<String> NOUNS_MAN = Arrays.asList(
            "alarm", "process", "sensor", "batch", "parts", "build", "schedule",
            "line", "material", "machine", "toolSpec", "unit", "controller",
            "actuator", "conveyor", "dye", "engine", "fixture", "gantry",
            "hopper", "inventory", "jig", "kiln", "mold", "nozzle", "operator",
            "pallet", "queue", "raw-material", "spindle", "valve", "assembly",
            "chassis", "workstation", "compressor", "generator", "turbine"
    );

    public static final List<String> STATE_VARS_MAN = Arrays.asList(
            "status", "temperature", "pressure", "speed", "voltage", "current",
            "duration", "component_id", "threshold", "count", "rate", "position",
            "level", "power", "error_count", "air_flow", "battery_level",
            "cycle_time", "density", "efficiency", "friction", "humidity",
            "inert_gas_level", "joint_angle", "load_cell", "moisture", "noise_level",
            "output_yield", "quality_score", "rotation_speed", "torque", "vibration",
            "wear_level", "lubrication_index", "coolant_temp", "psi_reading"
    );

    // Network Infra

    public static final List<String> VERBS_NET = Arrays.asList(
            "route", "switch", "bridge", "watch", "trace", "capture",
            "allow", "deny", "block", "save", "load", "update", "setup",
            "ping", "connect", "disconnect", "authenticate", "broadcast",
            "configure", "encrypt", "decrypt", "filter", "forward", "handshake",
            "isolate", "lease", "map", "optimize", "propagate", "release",
            "synchronize", "tunnel", "whitelist", "blacklist", "throttle",
            "advertise", "broadcast", "peer", "inspect"
    );

    public static final List<String> NOUNS_NET = Arrays.asList(
            "device", "module", "channel", "config", "interface", "network",
            "node", "router", "repeater", "location", "firewall", "packet", "port", "service",
            "access-point", "backbone", "bridge", "client", "datagram", "ethernet",
            "gateway", "host", "infrastructure", "jumper", "kernel", "load-balancer",
            "mainframe", "nat", "ontology", "proxy", "server", "topology", "endpoint",
            "vlan", "subnet", "switchport", "trunk", "uplink", "downlink"
    );

    public static final List<String> STATE_VARS_NET = Arrays.asList(
            "latency", "throughput", "status_code", "ip_address", "subnet_mask",
            "gateway", "port_no", "mac_address", "interface_name", "bandwidth",
            "packet_loss", "protocol_type", "session_id", "dns_server", "vlan_id", "route_metric",
            "uptime", "mtu", "ttl", "jitter", "signal_strength", "channel_width",
            "noise_floor", "lease_time", "hop_count", "retransmit_rate", "collision_count",
            "buffer_size", "cpu_usage", "memory_usage", "rx_bytes", "tx_bytes",
            "connection_count", "drop_rate", "latency_jitter", "power_level"
    );



    public static final List<String> COMMON_OPTS = Arrays.asList(
            "help", "version", "verbose", "quiet",
            "debug", "dry-run", "simulate", "output",
            "input", "timeout", "retry", "interactive",
            "confirm", "force"
    );




    public String generateToolName(Domain domain) {
        return domain.name().substring(0, 3).toUpperCase() + "-" +
                getRandomNoun(domain).toUpperCase() + "-" +
                (100 + random.nextInt(900)); // 100 is used to get atleast a 3 dig number
    }


     //Returns a random verb  for the given domain.

    public String getRandomVerb(Domain domain) {
        return switch (domain) {
            case MANUFACTURING -> pickOne(VERBS_MAN);
            case NETWORK_INFRA -> pickOne(VERBS_NET);
            default -> pickOne(VERBS_MAN);
        };
        }


    /**
     * Returns a random noun for a domain
     */
    public String getRandomNoun(Domain domain) {
        switch (domain) {
            case MANUFACTURING:
                return pickOne(NOUNS_MAN);
            case NETWORK_INFRA:
                return pickOne(NOUNS_NET);
            default:
                return pickOne(NOUNS_MAN);
        }
    }


// --------------------------------------------------------------------------------------
// OPTIONS

    // OptionSpec (name + description) for docs + NL hints
    public OptionSpec getRandomCommonOptionSpec() {
        String optionNameFromDict = pickOne(COMMON_OPTS);
        return new OptionSpec("--" + optionNameFromDict, getCommonFlagDescription(optionNameFromDict));
    }
    /**
     * Converts an option name like \`--simulate\` into a NL hint
     * using the same mapping as docs.
     */
    public static String hintFromOptionSpec(OptionSpec optionSpec) {
        if (optionSpec == null || optionSpec.description() ==null)return null;


        String desc = optionSpec.description().trim();
        return desc.isEmpty()?  null : desc;
    }

    private static String getCommonFlagDescription(String optionNameFromDict) {
        return switch (optionNameFromDict) {
            case "help" -> "Show help information and exit.";
            case "version" -> "Show version information and exit.";
            case "verbose" -> "Enable verbose logging/output.";
            case "quiet" -> "Suppress non-essential output.";
            case "debug" -> "Enable debug output.";
            case "dry-run" -> "Simulate execution without making changes.";
            case "simulate" -> "Run in simulation mode.";
            case "output" -> "Write output to a file.";
            case "input" -> "Read input from a file.";
            case "timeout" -> "Set a timeout for the operation.";
            case "retry" -> "Retry the operation on failure.";
            case "interactive" -> "Prompt for confirmations interactively.";
            case "confirm" -> "Require explicit confirmation before proceeding.";
            case "force" -> "Force the operation even if warnings are present.";
            default -> "Enable " + optionNameFromDict + " mode.";
        };
    }

// --------------------------------------------------------------------------------------
// STATE

    public String getRandomStateVariable(Domain domain) {
        switch (domain) {
            case MANUFACTURING:
                return pickOne(STATE_VARS_MAN);
            case NETWORK_INFRA:
                return pickOne(STATE_VARS_NET);
            default:
                return pickOne(STATE_VARS_MAN);
        }
    }


    private String pickOne(List<String> list) {
        return list.get(random.nextInt(list.size()));
    }
}
