package io.spring.initializr.geoip

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * @author Stephane Nicoll
 */
@ConfigurationProperties("geoip")
class GeoIpProperties {

	String elasticUrl = 'http://localhost:9200'

	boolean dryRun

	int batchSize = 250

}
