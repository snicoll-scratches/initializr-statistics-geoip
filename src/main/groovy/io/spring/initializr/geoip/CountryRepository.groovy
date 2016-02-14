package io.spring.initializr.geoip

import java.sql.ResultSet
import java.sql.SQLException

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.ResultSetExtractor
import org.springframework.stereotype.Component

/**
 * @author Stephane Nicoll
 */
@Component
class CountryRepository {

	private final Map<String, String> countryToIsoCode = [:]

	String getIsoCode(String country) {
		countryToIsoCode[country]
	}

	@Autowired
	public void initialize(JdbcTemplate jdbcTemplate) {
		jdbcTemplate.query('SELECT code, iso_code_2 FROM ip2nationCountries',
				new ResultSetExtractor<Void>() {
					@Override
					Void extractData(ResultSet rs) throws SQLException, DataAccessException {
						while (rs.next()) {
							countryToIsoCode[rs.getString('code')] = rs.getString('iso_code_2')
						}
						return null
					}
				})


	}
}
