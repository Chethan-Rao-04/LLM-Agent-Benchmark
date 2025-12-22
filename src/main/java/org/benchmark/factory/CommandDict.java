package org.benchmark.factory;

import java.util.Arrays;
import java.util.Random;


import java.util.List;


/**
 * A dictionary of commands terms for our domains (to be re-checked)
 */



public class CommandDict {
    private final Random random;

    public CommandDict(Random random){
        this.random = random;
    }
    public static final List<String> DOMAINS = List.of(
            "Manufacturing", "Network", "Medical"
    );

    //  DOMAIN SPECIFIC VERBS

    // Manufacturinf
    private static final List<String> VERBS_MAN = Arrays.asList(
            "start", "stop", "execute", "pause", "resume", "shutdown", "restart",
            "abort", "reboot", "calibrate", "measure", "inspect", "run",
            "load", "unload", "operate", "set", "unset"
    );

    // network
    private static final List<String> VERBS_NET = Arrays.asList(
            "route", "switch", "bridge", "watch", "trace", "capture",
            "allow", "deny", "block", "save", "load", "update", "setup",
            "ping", "connect", "disconnect" // Added a few common net terms if needed
    );

    // Medical ( required????)
    private static final List<String> VERBS_MED = Arrays.asList(
            "monitor", "authorize", "admit", "discharge", "assign", "transfer",
            "dispense", "audit", "retrieve", "calibrate", "inspect", "scan"
    );

    //  NOUNS

    private static final List<String> NOUNS_MAN = Arrays.asList(
            "alarm", "process", "sensor", "batch", "parts", "build", "schedule",
            "line", "material", "machine", "toolSpec", "unit", "controller"
    );

    private static final List<String> NOUNS_NET = Arrays.asList(
            "device", "module", "channel", "config", "interface", "network",
            "node", "router", "repeater", "location", "firewall", "packet", "port", "service"
    );

    private static final List<String> NOUNS_MED = Arrays.asList(
            "report", "patient", "scan", "test", "lab", "appointment",
            "medication", "xray-machine", "mri-machine", "icu", "incubator"
    );

    //  DOMAIN SPECIFIC STATE VARIABLES

    private static final List<String> STATE_VARS_MAN = Arrays.asList(
            "status", "temperature", "pressure", "speed", "voltage", "current",
            "duration", "component_id", "threshold", "count", "rate", "position",
            "level", "power", "error_count"
    );

    private static final List<String> STATE_VARS_NET = Arrays.asList(
            "latency", "throughput", "status_code", "ip_address", "subnet_mask",
            "gateway", "port_no", "mac_address", "interface_name", "bandwidth",
            "packet_loss", "protocol_type", "session_id", "dns_server", "vlan_id", "route_metric"
    );

    private static final List<String> STATE_VARS_MED = Arrays.asList(
            "patient_id", "heart_rate", "blood_pressure", "oxygen_level", "test_result",
            "timestamp", "device_status", "measurement_value", "diagnosis_code"
    );



    public List<String> getAllDomains() {
        return DOMAINS;
    }



    public String generateToolName(String domain) {
        return domain.substring(0, 3).toUpperCase() + "-" +
                getRandomNoun(domain).toUpperCase() + "-" +
                (100 + random.nextInt(900));
    }

    /**
     * Returns a random verb appropriate for the given domain.
     */
    public String getRandomVerb(String domain) {
        if (domain.equalsIgnoreCase("Manufacturing")) {
            return pickOne(VERBS_MAN);
        } else if (domain.equalsIgnoreCase("Network")) {
            return pickOne(VERBS_NET);
        } else if (domain.equalsIgnoreCase("Medical")) {
            return pickOne(VERBS_MED);
        }
        return pickOne(VERBS_MAN); // Default
    }

    /**
     * Returns a random noun for a domain
     */
    public String getRandomNoun(String domain) {
        if (domain.equalsIgnoreCase("Manufacturing")) {
            return pickOne(NOUNS_MAN);
        } else if (domain.equalsIgnoreCase("Network")) {
            return pickOne(NOUNS_NET);
        } else if (domain.equalsIgnoreCase("Medical")) {
            return pickOne(NOUNS_MED);
        }
        return pickOne(NOUNS_MAN); // Default
    }


    /**
     * Returns a random state variable for a domain
     */
    public String getRandomStateVariable(String domain) {
        if (domain.equalsIgnoreCase("Manufacturing")) {
            return pickOne(STATE_VARS_MAN);
        } else if (domain.equalsIgnoreCase("Network")) {
            return pickOne(STATE_VARS_NET);
        } else if (domain.equalsIgnoreCase("Medical")) {
            return pickOne(STATE_VARS_MED);
        }
        return pickOne(STATE_VARS_MAN); // Default
    }


    private String pickOne(List<String> list) {
        return list.get(random.nextInt(list.size()));
    }
}

