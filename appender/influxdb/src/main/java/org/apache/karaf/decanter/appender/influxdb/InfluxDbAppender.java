/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.karaf.decanter.appender.influxdb;

import org.apache.karaf.decanter.appender.utils.EventFilter;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component(
        name = "org.apache.karaf.decanter.appender.influxdb",
        immediate = true,
        property = EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
public class InfluxDbAppender implements EventHandler {

    private Dictionary<String, Object> config;

    private InfluxDB influxDB;

    @Activate
    public void activate(ComponentContext componentContext) {
        activate(componentContext.getProperties());
    }

    public void activate(Dictionary<String, Object> config) {
        this.config = config;
        if (config.get("url") == null) {
            throw new IllegalArgumentException("url property is mandatory");
        }
        String url = (String) config.get("url");
        String username = null;
        if (config.get("username") != null) {
            username = (String) config.get("username");
        }
        String password = null;
        if (config.get("password") != null) {
            password = (String) config.get("password");
        }
        if (username != null) {
            this.influxDB = InfluxDBFactory.connect(url, username, password);
        } else {
            this.influxDB = InfluxDBFactory.connect(url);
        }
        String database = "decanter";
        if (config.get("database") != null) {
            database = (String) config.get("database");
        }
        this.influxDB.enableBatch(100, 200, TimeUnit.MILLISECONDS);
        this.influxDB.setRetentionPolicy("defaultPolicy");
        this.influxDB.setDatabase(database);
    }

    @Deactivate
    public void deactivate() {
        if (influxDB != null) {
            influxDB.close();
        }
    }

    @Override
    public void handleEvent(Event event) {
        if (EventFilter.match(event, config)) {
            String type = "decanter";
            if (event.getProperty("type") != null) {
                type = (String) event.getProperty("type");
            }
            Map<String, Object> data = new HashMap<>();
            for (String propertyName : event.getPropertyNames()) {
                Object propertyValue = event.getProperty(propertyName);
                data.put(propertyName, propertyValue);
            }
            Point point = Point.measurement(type).fields(data).build();
            influxDB.write(point);
        }
    }

}
