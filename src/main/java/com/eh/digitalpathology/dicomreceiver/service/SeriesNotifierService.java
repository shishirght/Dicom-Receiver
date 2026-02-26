package com.eh.digitalpathology.dicomreceiver.service;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;

@Service
@RefreshScope
public class SeriesNotifierService {

    @Value( "${visiopharm.url}" )
    private String visiopharmUrl;

    private static final Logger logger = LoggerFactory.getLogger(SeriesNotifierService.class);

    public void notifyVisiopharm(String seriesUrl)  {

        String jsonReq = "{\"DICOMSeries\": \"" + seriesUrl + "\"}";
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            logger.debug("notifyVisiopharm :: Creating connection with Visiopharm.");

            HttpPost request = new HttpPost(visiopharmUrl);
            request.setHeader("Content-Type", "application/json");
            request.setEntity(new StringEntity(jsonReq));

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseEntity = EntityUtils.toString(response.getEntity());
                if (statusCode == 200) {
                    logger.info("notifyVisiopharm :: Successfully send notification to visiopharm");
                } else if (statusCode >= 400 && statusCode < 500) {
                    logger.error("notifyVisiopharm :: Client error: {} {}", statusCode, responseEntity);
                } else if (statusCode >= 500) {
                    logger.error("notifyVisiopharm :: Server error: {} {}", statusCode, responseEntity);
                }
            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            logger.error("notifyVisiopharm :: VisiopharmSeriesNotifierException occurred while notifying visiopharm ", e);
        }
    }
}
