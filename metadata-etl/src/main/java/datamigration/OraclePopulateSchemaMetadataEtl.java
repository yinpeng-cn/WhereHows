/**
 * Copyright 2015 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */

package datamigration;

import com.linkedin.common.callback.FutureCallback;
import com.linkedin.common.util.None;
import com.linkedin.r2.transport.common.Client;
import com.linkedin.r2.transport.common.bridge.client.TransportClientAdapter;
import com.linkedin.r2.transport.http.client.HttpClientFactory;
import com.linkedin.restli.client.RestClient;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import metadata.etl.EtlJob;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import wherehows.common.Constant;
import wherehows.common.utils.JdbcConnection;
import wherehows.restli.dataset.client.OracleCreateSchemaMetadata;


public class OraclePopulateSchemaMetadataEtl extends EtlJob {

  @Deprecated
  public OraclePopulateSchemaMetadataEtl(int dbId, long whExecId) {
    super(null, dbId, whExecId);
  }

  public OraclePopulateSchemaMetadataEtl(int dbId, long whExecId, Properties prop) {
    super(null, dbId, whExecId, prop);
  }

  public static final String GET_OWNER_BY_ID = "SELECT * FROM dataset_owner WHERE dataset_urn like :platform limit 1";
  public final static String GET_DATASETS_NAME_LIKE_PLAT =
      "SELECT * FROM dict_dataset WHERE urn like :platform limit 1";

  /** The property_name field in wh_property table for WhereHows database connection information */
  public String driverClassName = prop.getProperty(Constant.WH_DB_DRIVER_KEY);
  public String url = prop.getProperty(Constant.WH_DB_URL_KEY);
  public String dbUserName = prop.getProperty(Constant.WH_DB_USERNAME_KEY);
  public String dbPassword = prop.getProperty(Constant.WH_DB_PASSWORD_KEY);
  public String restLiServerURL = prop.getProperty(Constant.WH_RESTLI_SERVER_URL);

  OracleCreateSchemaMetadata lRestClient = new OracleCreateSchemaMetadata();

  public void populate()
      throws SQLException {

    // Create an HttpClient and wrap it in an abstraction layer
    final HttpClientFactory http = new HttpClientFactory();
    final Client r2Client = new TransportClientAdapter(http.getClient(Collections.<String, String>emptyMap()));
    RestClient _restClient = new RestClient(r2Client, restLiServerURL);
    logger.debug("restLiServerURL is: " + restLiServerURL);
    System.out.println("restLiServerURL is: " + restLiServerURL);

    NamedParameterJdbcTemplate
        lNamedParameterJdbcTemplate = JdbcConnection.getNamedParameterJdbcTemplate(driverClassName,url,dbUserName,dbPassword);

    Map<String, Object> OwnerParams = new HashMap<>();
    Map<String, Object> ownerQueryResult = new HashMap<>();
    OwnerParams.put("platform", "oracle:///%");
    ownerQueryResult = lNamedParameterJdbcTemplate.queryForMap(GET_OWNER_BY_ID, OwnerParams);

    Map<String, Object> params = new HashMap<>();
    params.put("platform", "oracle:///%");
    List<Map<String, Object>> rows = null;
    rows = lNamedParameterJdbcTemplate.queryForList(GET_DATASETS_NAME_LIKE_PLAT, params);
    logger.info("Total number of rows is: " + rows.size());

    for (Map row : rows) {
      String lSchemaName = (String) row.get("parent_name") + '.' + (String) row.get("name");
      Object lSchema = row.get("schema");
      String lDatasetName = (String) row.get("parent_name") + '.' + (String) row.get("name");
      String lOwnerName = ownerQueryResult.get("namespace") + ":" + ownerQueryResult.get("owner_id");
      logger.info("The populating schema is: " + lSchema);

      if (true) // not found then create, avoid committing transaction error. Is this necessary?
      {
        try {
          lRestClient.create(_restClient, lSchemaName,lSchema,lDatasetName,lOwnerName); //"urn:li:corpuser:gmohanas"
        } catch (Exception e) {
          logger.error(e.toString());
        }
      }
    }

    // shutdown
    _restClient.shutdown(new FutureCallback<None>());
    http.shutdown(new FutureCallback<None>());
  }


  @Override
  public void extract()
      throws Exception {
  }

  @Override
  public void transform()
      throws Exception {
  }

  @Override
  public void load()
      throws Exception {
  }

}