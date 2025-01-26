package com.graphhopper.example;

import java.io.File;

import org.junit.jupiter.api.Test;

import com.graphhopper.util.Helper;

public class RoutingExampleTest {

    @Test
    public void main() {
        Helper.removeDir(new File("target/routing-graph-cache"));
        RoutingExample.main(new String[]{"../"});

        Helper.removeDir(new File("target/routing-tc-graph-cache"));
        RoutingExampleTC.main(new String[]{"../"});
    }
}
