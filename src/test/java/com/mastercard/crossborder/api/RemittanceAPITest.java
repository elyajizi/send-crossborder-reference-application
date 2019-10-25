package com.mastercard.crossborder.api;

import com.mastercard.crossborder.api.helper.CrossBorderAPITestHelper;
import com.mastercard.crossborder.api.rest.request.QuotesRequest;
import com.mastercard.crossborder.api.rest.request.RemittanceRequest;
import com.mastercard.crossborder.api.rest.response.Error;
import com.mastercard.crossborder.api.rest.response.Errors;
import com.mastercard.crossborder.api.rest.response.Proposal;
import com.mastercard.crossborder.api.rest.response.QuotesResponse;
import com.mastercard.crossborder.api.rest.response.RemittanceResponse;
import com.mastercard.crossborder.api.config.MastercardApiConfig;
import com.mastercard.crossborder.api.exception.ServiceException;
import com.mastercard.crossborder.api.rest.QuotesAPI;
import com.mastercard.crossborder.api.rest.RemittanceAPI;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/*
   Payment:-
   This class covers all the use cases to make a payment
   Payment can be made in two ways
   a) Make payment using quote – OI needs to pass quote details (proposal ID) while making payment.
   b) One shot payment (payment without quotes) – OI can directly make a payment without requesting a quote.
*/
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {MastercardApiConfig.class})
public class RemittanceAPITest  {

    private String partnerId;

    @Autowired
    RemittanceAPI remittanceAPI;

    @Autowired
    QuotesAPI quotesAPI;

    @Autowired
    MastercardApiConfig apiConfig;

    private static final Logger logger = LoggerFactory.getLogger(RemittanceAPITest.class);

    @Before
    public void init() {
        partnerId = apiConfig.getPartnerId();
    }
    /*
       #Usecase - 1 - **PAYMENT WITH FORWARD QUOTE**
       Make a forward quote and use same proposal ID to make payment
       NOTE: Proposal returned from quotes response is time bound
    */
    @Test
    public void testPaymentWithQuote() {
        logger.info("Running Usecase - 1, PAYMENT WITH FORWARD QUOTE.");
        Map<String, Object> requestParams = new HashMap<>();
        requestParams.put("partner-id", partnerId);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML);

        try {
            QuotesRequest request = CrossBorderAPITestHelper.setDataForForwardQuote();
            QuotesResponse quotesResponse = quotesAPI.getQuote(headers, requestParams, request);
            Optional proposal = quotesResponse.getProposals().getProposal().stream().findFirst();
            if ( proposal.isPresent()) {
                String ProposalId = ((Proposal) proposal.get()).getProposalId();
                RemittanceRequest paymentRequest = CrossBorderAPITestHelper.setPaymentDataWithQuote(ProposalId);
                RemittanceResponse paymentDetails = remittanceAPI.makePayment(headers, requestParams, paymentRequest);
                if (null != paymentDetails) {
                    String paymentId = paymentDetails.getRemittanceId();
                    Assert.assertNotNull(paymentId);
                    //This is to verify that quote created above is used to make a payment. Same amount is charged to make a payment as that of quote
                    assertEquals( paymentDetails.getCreditedAmount().getAmount(), ((Proposal) proposal.get()).getCreditedAmount().getAmount());
                    logger.info("Payment with quote is successful, paymentId is {}", paymentId);
                } else {
                    Assert.fail("Payment with quote has failed as Payment API has failed");
                    logger.info("Payment with quote has failed as Payment API has failed");
                }
            }
            else {
                Assert.fail("Payment with quote has failed as quotes API has failed");
                logger.info("Payment with quote has failed as quotes API has failed");
            }
        } catch (ServiceException re){
            Assert.fail(re.getMessage());
            logger.error("Payment with quote has failed for the error {}", re.getMessage());
        }
    }

    /*
      #Usecase - 2 - **FORWARD PAYMENT WITHOUT QUOTE**
      There is no separate call made to get the quote, in single API call we are making payment.
   */
    @Test
    public void testOneShotPaymentWithForwardQuote() {
        logger.info("Running Usecase - 2, FORWARD PAYMENT WITHOUT QUOTE.");

        Map<String, Object> requestParams = new HashMap<>();
        requestParams.put("partner-id", partnerId);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML);
        try {
            RemittanceRequest paymentRequest = CrossBorderAPITestHelper.setPaymentDataForwardQuote();
            RemittanceResponse paymentDetails = remittanceAPI.makePayment(headers, requestParams, paymentRequest);

            if (null != paymentDetails) {
                String paymentId = paymentDetails.getRemittanceId();
                Assert.assertNotNull(paymentId);
                logger.info("One shot payment is successful, paymentId is {}", paymentId);
            } else {
                Assert.fail("One shot payment has failed.");
                logger.info("One shot payment has failed.");
            }
        }
        catch (ServiceException re){
            Assert.fail(re.getMessage());
            logger.error("One shot payment API has failed for the error {}", re.getMessage());
        }
    }
    /*
         #Usecase - 3 - **REVERSE PAYMENT WITHOUT QUOTE**
         There is no separate call made to get the quote, in a single API call we are making payment
      */
    @Test
    public void testOneShotPaymentWithReverseQuote() {
        logger.info("Running Usecase - 3, REVERSE PAYMENT WITHOUT QUOTE.");

        Map<String, Object> requestParams = new HashMap<>();
        requestParams.put("partner-id", partnerId);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML);
        try {
            RemittanceRequest paymentRequest = CrossBorderAPITestHelper.setPaymentDataForReverseQuote();
            RemittanceResponse paymentDetails = remittanceAPI.makePayment(headers, requestParams, paymentRequest);
            if (null != paymentDetails) {
                String paymentId = paymentDetails.getRemittanceId();
                Assert.assertNotNull(paymentId);
                logger.info("One shot payment is successful, paymentId is {}", paymentId);
            } else {
                Assert.fail("One shot payment has failed.");
                logger.info("One shot payment has failed.");
            }
        }
        catch (ServiceException re){
            Assert.fail(re.getMessage());
            logger.error("One shot payment has failed for the error {}", re.getMessage());
        }
    }

    /*
      #Usecase - 4 - **ERROR HANDLING**
      Payment can fail for various reasons, this scenario is added so that user should know what to expect where there is a failure
      Refer https://developer.mastercard.com/documentation/mastercard-send-cross-border/1#error-codes for details
   */
    @Test
    public void testErrorHandling() {
        logger.info("Running Usecase - 4, ERROR HANDLING.");
        Map<String, Object> requestParams = new HashMap<>();
        requestParams.put("partner-id", partnerId);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML);
        try {
            RemittanceRequest paymentRequest = CrossBorderAPITestHelper.setPaymentDataRejectedStatus();
            remittanceAPI.makePayment(headers, requestParams, paymentRequest);
            Assert.fail("Payment has to fail for wrong proposal ID");
        } catch (ServiceException se){
            Errors errors = se.getErrors();
            Assert.assertFalse(errors.getErrors().isEmpty());
            for (Error error : errors.getErrors()) {
                assertEquals("proposal_id", error.getSource());
                assertEquals("INVALID_INPUT_VALUE", error.getReasonCode());
            }
            logger.error("Payment with quote has failed for the error {}", se.getMessage());
        }
    }

    /* #Usecase - 5 - **PAYMENT WITH ENCRYPTION SUPPORTED**
       By making a call to makePaymentWithEncryption, we are encrypting payment request and response
     */
    @Test
    public void testOneShotPaymentWithEncryption() {
        if (apiConfig.getRunWithEncryptedPayload()) {
            logger.info("Running Usecase - 5, PAYMENT WITH ENCRYPTION SUPPORTED.");
            Map<String, Object> requestParams = new HashMap<>();
            requestParams.put("partner-id", partnerId);
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML);
            try {
                RemittanceRequest paymentRequest = CrossBorderAPITestHelper.setPaymentDataForwardQuote();
                RemittanceResponse paymentDetails = remittanceAPI.makePaymentWithEncryption(headers, requestParams, paymentRequest);
                if (null != paymentDetails) {
                    String paymentId = paymentDetails.getRemittanceId();
                    Assert.assertNotNull(paymentId);
                    logger.info("One shot payment with encryption is successful, paymentId is {}", paymentId);
                } else {
                    Assert.fail("One shot payment with encryption has failed.");
                    logger.info("One shot payment with encryption has failed.");
                }
            } catch (ServiceException re) {
                Assert.fail(re.getMessage());
                logger.error("One shot payment with encryption has failed for the error {}", re.getMessage());
            }
        }
        else
            logger.info("To run this use cases, Set runWithEncryptedPayload=true and other encryption / decryption keys in mastercard-api.properties.");
    }

}