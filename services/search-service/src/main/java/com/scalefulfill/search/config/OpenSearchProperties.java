package com.scalefulfill.search.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "opensearch")
public class OpenSearchProperties {
    private String host = "localhost";
    private int port = 9200;
    private String scheme = "http";
    private String ordersIndex = "orders-index";
    private String productsIndex = "products-index";

    public String getBaseUrl() {
        return scheme + "://" + host + ":" + port;
    }
}
