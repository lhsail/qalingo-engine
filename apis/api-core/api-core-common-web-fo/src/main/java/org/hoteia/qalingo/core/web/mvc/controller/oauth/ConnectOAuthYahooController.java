/**
 * Most of the code in the Qalingo project is copyrighted Hoteia and licensed
 * under the Apache License Version 2.0 (release version 0.8.0)
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *                   Copyright (c) Hoteia, 2012-2014
 * http://www.hoteia.com - http://twitter.com/hoteia - contact@hoteia.com
 *
 */
package org.hoteia.qalingo.core.web.mvc.controller.oauth;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.hoteia.qalingo.core.domain.AttributeDefinition;
import org.hoteia.qalingo.core.domain.Customer;
import org.hoteia.qalingo.core.domain.CustomerAttribute;
import org.hoteia.qalingo.core.domain.EngineSetting;
import org.hoteia.qalingo.core.domain.EngineSettingValue;
import org.hoteia.qalingo.core.domain.Market;
import org.hoteia.qalingo.core.domain.MarketArea;
import org.hoteia.qalingo.core.domain.enumtype.CustomerNetworkOrigin;
import org.hoteia.qalingo.core.domain.enumtype.FoUrls;
import org.hoteia.qalingo.core.domain.enumtype.OAuthType;
import org.hoteia.qalingo.core.mapper.JsonMapper;
import org.hoteia.qalingo.core.pojo.RequestData;
import org.hoteia.tools.scribe.mapping.oauth.twitter.json.pojo.UserPojo;
import org.scribe.builder.ServiceBuilder;
import org.scribe.builder.api.YahooApi;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;

/**
 * 
 */
@Controller("connectOAuthYahooController")
public class ConnectOAuthYahooController extends AbstractOAuthFrontofficeController {

	protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    protected JsonMapper jsonMapper;
    
	@RequestMapping("/connect-oauth-yahoo.html*")
	public ModelAndView connectYahoo(final HttpServletRequest request, final HttpServletResponse response) throws Exception {
		final RequestData requestData = requestUtil.getRequestData(request);
		
		// SANITY CHECK
		if(!requestUtil.hasKnownCustomerLogged(request)){
			try {
			    // CLIENT ID
			    EngineSetting clientIdEngineSetting = engineSettingService.getSettingOAuthAppKeyOrId();
			    EngineSettingValue clientIdEngineSettingValue = clientIdEngineSetting.getEngineSettingValue(OAuthType.YAHOO.name());
			    
			    // CLIENT SECRET
			    EngineSetting clientSecretEngineSetting = engineSettingService.getSettingOAuthAppSecret();
			    EngineSettingValue clientSecretEngineSettingValue = clientSecretEngineSetting.getEngineSettingValue(OAuthType.YAHOO.name());
			    
			    // CLIENT PERMISSIONS
			    EngineSetting permissionsEngineSetting = engineSettingService.getSettingOAuthAppPermissions();
			    EngineSettingValue permissionsEngineSettingValue = permissionsEngineSetting.getEngineSettingValue(OAuthType.YAHOO.name());
			    
			    if(clientIdEngineSettingValue != null
			    		&& clientSecretEngineSetting != null
			    		&& permissionsEngineSettingValue != null){
					final String clientId = clientIdEngineSettingValue.getValue();
					final String clientSecret = clientSecretEngineSettingValue.getValue();
					final String permissions = permissionsEngineSettingValue.getValue();
					
				    final String yahooCallBackURL = urlService.buildAbsoluteUrl(requestData, urlService.buildOAuthCallBackUrl(requestData, OAuthType.YAHOO.getPropertyKey().toLowerCase()));

			        OAuthService service = new ServiceBuilder()
			        .provider(YahooApi.class)
			        .apiKey(clientId)
			        .apiSecret(clientSecret)
			        .scope(permissions)
		            .callback(yahooCallBackURL)
		            .build();

			        Token requestToken = service.getRequestToken();
                    request.getSession().setAttribute(YAHOO_OAUTH_REQUEST_TOKEN, requestToken);
                    
                    // Obtain the Authorization URL
                    String authorizationUrl = service.getAuthorizationUrl(requestToken);

                    response.sendRedirect(authorizationUrl);
			    }

			} catch (Exception e) {
				logger.error("Connect With " + OAuthType.YAHOO.name() + " failed!");
			}
		}

		// DEFAULT FALLBACK VALUE
		if(!response.isCommitted()){
			response.sendRedirect(urlService.generateUrl(FoUrls.LOGIN, requestData));
		}
		
		return null;
	}
	
	   protected void handleAuthenticationData(HttpServletRequest request, HttpServletResponse response, RequestData requestData, OAuthType type, String jsonData) throws Exception {
	        UserPojo userPojo = null;
	        try {
	            userPojo = jsonMapper.getJsonMapper().readValue(jsonData, UserPojo.class);
	        } catch (JsonGenerationException e) {
	            logger.error(e.getMessage());
	        } catch (JsonMappingException e) {
	            logger.error(e.getMessage());
	        }
	        if(userPojo != null){
	            // TWITTER DOESN'T WANT TO SHARE EMAIL -> WE DON'T EXPOSE THIS CONNECT OPTION - WITHOUT EMAIL WE WILL SAVE DUPLICATES WITH CORE CREATE ACCOUNT PROCESS 
	            // AND WE DON'T WANT ASK TO THE NEW CUSTOMER TO CONFIRM HIS EMAIL BY A FORM
	            final String email = ""; 

	            final String firstName = userPojo.getName();
	            final String lastName = userPojo.getName();
	            final String locale = userPojo.getLang();
	            Customer customer = customerService.getCustomerByLoginOrEmail(email);
	            
	            if(customer == null){
	                final Market currentMarket = requestData.getMarket();
	                final MarketArea currentMarketArea = requestData.getMarketArea();
	                
	                // CREATE A NEW CUSTOMER
	                customer = new Customer();
//	              customer = setCommonCustomerInformation(request, customer);

	                customer.setLogin(email);
	                customer.setPassword(securityUtil.generatePassword());
	                customer.setEmail(email);
	                customer.setFirstname(firstName);
	                customer.setLastname(lastName);
	                
	                customer.setNetworkOrigin(CustomerNetworkOrigin.TWITTER.getPropertyKey());

	                CustomerAttribute attribute = new CustomerAttribute();
	                AttributeDefinition attributeDefinition = attributeService.getAttributeDefinitionByCode(CustomerAttribute.CUSTOMER_ATTRIBUTE_SCREENAME);
	                attribute.setAttributeDefinition(attributeDefinition);
	                String screenName = "";
	                if(StringUtils.isNotEmpty(lastName)){
	                    if(StringUtils.isNotEmpty(lastName)){
	                        screenName = lastName;
	                        if(screenName.length() > 1){
	                            screenName = screenName.substring(0, 1);
	                        }
	                        if(!screenName.endsWith(".")){
	                            screenName = screenName + ". ";
	                        }
	                    }
	                }
	                screenName = screenName + firstName;
	                attribute.setShortStringValue(screenName);
	                customer.getAttributes().add(attribute);
	                
	                if(StringUtils.isNotEmpty(locale)){
	                    customer.setDefaultLocale(locale);
	                }
	                
	                // Save the new customer
	                customer = webManagementService.buildAndSaveNewCustomer(requestData, currentMarket, currentMarketArea, customer);
	                
	                // Save the email confirmation
	                webManagementService.buildAndSaveCustomerNewAccountMail(requestData, customer);
	            }

	            // Redirect to the edit page
	            if(StringUtils.isNotEmpty(customer.getEmail())){
	                
	                // Login the new customer
	                securityRequestUtil.authenticationCustomer(request, customer);
	                
	                // Update the customer session
	                requestUtil.updateCurrentCustomer(request, customer);

	                response.sendRedirect(urlService.generateUrl(FoUrls.PERSONAL_EDIT, requestData));
	            }
	            
	        }
	    }
	
}