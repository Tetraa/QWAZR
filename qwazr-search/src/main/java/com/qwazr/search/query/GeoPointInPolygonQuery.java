/**
 * Copyright 2015-2016 Emmanuel Keller / QWAZR
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qwazr.search.query;

import com.qwazr.search.index.QueryContext;
import org.apache.lucene.search.Query;

import java.io.IOException;
import java.util.Objects;

public class GeoPointInPolygonQuery extends AbstractQuery {

	final public String field;

	final public double[] poly_lats;

	final public double[] poly_lons;

	public GeoPointInPolygonQuery() {
		field = null;
		poly_lats = null;
		poly_lons = null;
	}

	public GeoPointInPolygonQuery(final String field, final double[] polyLats, final double[] polyLons) {
		Objects.requireNonNull(field, "The field is null");
		Objects.requireNonNull(polyLats, "The poly_lats parameter is null");
		Objects.requireNonNull(polyLons, "The poly_lons parameter is null");
		this.field = field;
		this.poly_lats = polyLats;
		this.poly_lons = polyLons;
	}

	@Override
	final public Query getQuery(QueryContext queryContext) throws IOException {
		return new org.apache.lucene.spatial.geopoint.search.GeoPointInPolygonQuery(field, poly_lats, poly_lons);
	}
}
