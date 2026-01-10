package org.benchmark.factory;

import org.benchmark.model.tool.Domain;

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

    // health care

    public static final List<String> VERBS_MED = Arrays.asList(
            "monitor", "authorize", "admit", "discharge", "assign", "transfer",
            "dispense", "audit", "retrieve", "calibrate", "inspect", "scan",
            "anesthetize", "bill", "consult", "diagnose", "evaluate", "intubate",
            "prescribe", "record", "refer", "screen", "treat", "vaccinate",
            "resuscitate", "sterilize", "implant", "extract", "amplify", "stabilize"
    );

    public static final List<String> NOUNS_MED = Arrays.asList(
            "report", "patient", "scan", "test", "lab", "appointment",
            "medication", "xray-machine", "mri-machine", "icu", "incubator",
            "bandage", "chart", "clinic", "dosage", "ekg", "folder", "gown",
            "gurney", "hospital", "infusion", "kit", "ledger", "nursery",
            "pharmacy", "specimen", "ward", "stretcher", "ventilator", "monitor",
            "cart", "prescription", "diagnostic", "vitals", "biopsy"
    );

    public static final List<String> STATE_VARS_MED = Arrays.asList(
            "patient_id", "heart_rate", "blood_pressure", "oxygen_level", "test_result",
            "timestamp", "device_status", "measurement_value", "diagnosis_code",
            "glucose_level", "body_temp", "respiratory_rate", "bmi", "cholesterol_count",
            "hydration_status", "pain_scale", "white_cell_count", "pulse_oximetry",
            "hemoglobin", "bilirubin", "creatinine", "sodium_level", "potassium_level",
            "calcium_level", "platelet_count", "iv_drip_rate", "oxygen_saturation"
    );



    public List<Domain> getAllDomains() {
        return DOMAINS;
    }



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
            case HEALTHCARE -> pickOne(VERBS_MED);
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
            case HEALTHCARE:
                return pickOne(NOUNS_MED);
            default:
                return pickOne(NOUNS_MAN);
        }
    }


    // bug, need to update state var model
    public String getRandomStateVariable(Domain domain) {
        switch (domain) {
            case MANUFACTURING:
                return pickOne(STATE_VARS_MAN);
            case NETWORK_INFRA:
                return pickOne(STATE_VARS_NET);
            case HEALTHCARE:
                return pickOne(STATE_VARS_MED);
            default:
                return pickOne(STATE_VARS_MAN);
        }
    }


    private String pickOne(List<String> list) {
        return list.get(random.nextInt(list.size()));
    }
}

