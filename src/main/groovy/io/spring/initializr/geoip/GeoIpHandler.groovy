package io.spring.initializr.geoip

import java.sql.PreparedStatement
import java.sql.SQLException

import groovy.util.logging.Slf4j
import io.searchbox.client.JestClient
import io.searchbox.client.JestResult
import io.searchbox.core.Bulk
import io.searchbox.core.Search
import io.searchbox.core.SearchResult
import io.searchbox.core.SearchScroll
import io.searchbox.core.Update
import io.searchbox.params.Parameters

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.PreparedStatementCallback
import org.springframework.stereotype.Service

/**
 * @author Stephane Nicoll
 */
@Service
@Slf4j
class GeoIpHandler implements CommandLineRunner {

	public static
	final String FETCH_COUNTRY_QUERY = 'SELECT country FROM ip2nation WHERE ip < INET_ATON(?) ORDER BY ip DESC LIMIT 0,1'


	private final JdbcTemplate jdbcTemplate
	private final JestClient jestClient
	private final CountryRepository countryRepository
	private final GeoIpProperties properties

	@Autowired
	GeoIpHandler(JdbcTemplate jdbcTemplate, JestClient jestClient,
				 CountryRepository countryRepository, GeoIpProperties properties) {
		this.jdbcTemplate = jdbcTemplate
		this.jestClient = jestClient
		this.countryRepository = countryRepository
		this.properties = properties
	}

	private int notRequired
	private int noIp
	private int success
	private int failed
	private int total

	@Override
	void run(String... args) throws Exception {
		process()
	}

	def all = []

	private void process() {
		int size = this.properties.batchSize
		SearchResult initialSearch = search(size)
		String nextScrollId = handleSearchResult(initialSearch)
		while (nextScrollId) {
			nextScrollId = handlePage(nextScrollId, size)
		}

		log.info("Stats")
		log.info("Identifiers " + all.size())
		log.info("Total $total")
		log.info("info already available $notRequired")
		log.info("not ip available $noIp")
		log.info("country found $success")
		log.info("no country found $failed")
	}

	private String handlePage(String scrollId, int size) {
		log.info("Processing next page of $size records")
		SearchScroll scroll = new SearchScroll.Builder(scrollId, "2m")
				.setParameter(Parameters.SIZE, size).build();
		JestResult result = jestClient.execute(scroll)
		return handleSearchResult(result)
	}

	private String handleSearchResult(JestResult result) {
		if (result.isSucceeded()) {
			def hits = result.getSourceAsObjectList(GeoInfo.class)
			total += hits.size()
			if (hits.size() > 0) {
				Collection<GeoInfo> updates = []
				hits.each { i ->
					if (all.contains(i.id)) {
						log.error("That idenfifier was already processed! $i.source.id")
					} else {
						all << i.id
					}
					def item = handleItem(i)
					if (item) {
						updates << item
					}
				}
				if (!this.properties.dryRun) {
					update(updates)
				}
				return result.getJsonObject().get("_scroll_id").getAsString();
			} else {
				log.info("No more results")
			}
		} else {
			log.error('Search failed ' + result.errorMessage)
		}
		return null;
	}

	private GeoInfo handleItem(GeoInfo info) {
		if (info.requestCountry) {
			notRequired++
		} else if (!info.requestIpv4) {
			noIp++
		} else {
			log.debug("Handling $info.requestIpv4")
			def country = getCountryForIp(info.requestIpv4)
			if (country) {
				String code = countryRepository.getIsoCode(country)
				if (!code) {
					log.warn("No code found for country '$country'")
					failed++
				} else {
					log.trace("Country is $country and ISO code is $code")
					success++
					info.requestCountry = code
					return info
				}
			} else {
				log.warn("No country found for $info.requestIpv4")
				failed++
			}
		}
		return null
	}

	private String getCountryForIp(String ip) {
		jdbcTemplate.execute(FETCH_COUNTRY_QUERY, new PreparedStatementCallback<String>() {
			@Override
			String doInPreparedStatement(PreparedStatement ps) throws SQLException, DataAccessException {
				ps.setString(1, ip)
				def rs = ps.executeQuery()
				try {
					if (rs.next()) {
						return rs.getString('country')
					}
					return null
				} finally {
					rs.close()
				}
			}
		})
	}

	private SearchResult search(int size) {
		String query =
				'{ "query": { "filtered": {"filter":{ "missing": {"field": "requestCountry"}}}}, ' +
						'"sort": [ { "generationTimestamp": {"order": "desc"}}]}'


		Search search = new Search.Builder(query)
				.addIndex("initializr")
				.addType('request')
				.setParameter(Parameters.SIZE, size)
				.setParameter(Parameters.SCROLL, "2m")
				.build();
		jestClient.execute(search)
	}

	private update(Collection<GeoInfo> content) {
		if (!content) {
			return
		}
		log.info(content.size() + ' elements need to be updated')
		def builder = new Bulk.Builder()
				.defaultIndex("initializr")
				.defaultType("request")
		content.each { i ->
			String script = '{ "doc" : { "requestCountry" : "' + i.requestCountry + '"' + '} }'
			builder.addAction(new Update.Builder(script).id(i.id).build())
		}
		def result = jestClient.execute(builder.build())
		if (result.errorMessage) {
			log.error("Bulk update failed $result.errorMessage")
		}
	}

}
