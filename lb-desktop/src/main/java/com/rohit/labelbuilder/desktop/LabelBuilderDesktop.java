package com.rohit.labelbuilder.desktop;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring configuration root for the desktop application.
 *
 * <p>Component scanning is deliberately limited to the desktop package; services from lower
 * modules (lb-core, lb-data, …) are registered explicitly via {@code @Configuration} classes as
 * those modules gain real code, keeping bean visibility intentional.
 */
@SpringBootApplication
public class LabelBuilderDesktop {}
