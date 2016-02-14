package io.spring.initializr.geoip

import groovy.transform.ToString
import io.searchbox.annotations.JestId

/**
 * @author Stephane Nicoll
 */
@ToString(ignoreNulls = true, includePackage = false, includeNames = true)
class GeoInfo {

	@JestId
	String id

	String requestIpv4

	String requestCountry




}
