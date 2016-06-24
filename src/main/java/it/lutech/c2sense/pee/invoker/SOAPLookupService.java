package it.lutech.c2sense.pee.invoker;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.juddi.api_v3.AccessPointType;
import org.apache.juddi.v3.client.UDDIConstants;
import org.apache.juddi.v3.client.config.UDDIClient;
import org.apache.juddi.v3.client.transport.Transport;
import org.apache.juddi.v3.client.transport.TransportException;
import org.uddi.api_v3.*;
import org.uddi.v3_service.UDDIInquiryPortType;
import org.uddi.v3_service.UDDISecurityPortType;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by massimo on 13/05/16.
 */
public class SOAPLookupService {

    public static final String VAR_WSDL_URI = "VAR_WSDL_URI";
    private static UDDISecurityPortType security;
    private static UDDIInquiryPortType inquiry;

    private static  SOAPLookupService instance;

    private SOAPLookupService() throws TransportException, ConfigurationException {
        // create a client and read the config in the archive;
        // you can use your config file name
//        UDDIClient uddiClient = new UDDIClient("/home/massimo/lavoro/c2sense/workspace/pee/conf/env/dev/uddi.xml");
        UDDIClient uddiClient = new UDDIClient("uddi.xml");
        // a UddiClient can be a client to multiple UDDI nodes, so
        // supply the nodeName (defined in your uddi.xml.
        // The transport can be WS, inVM, RMI etc which is defined in the uddi.xml
        Transport transport = uddiClient.getTransport("default");
        // Now you create a reference to the UDDI API
        security = transport.getUDDISecurityService();
        inquiry = transport.getUDDIInquiryService();
    }

    public static SOAPLookupService getInstance() throws ServiceConfigurationException {
        if(instance == null){
            try {
                instance = new SOAPLookupService();
            } catch (TransportException | ConfigurationException e) {
                throw new ServiceConfigurationException(e);
            }
        }
        return instance;
    }

    public Map<String, String> lookup(String businessKey, String serviceName, String bindingKey, String userId, String credential) throws Exception {
        String token = getAuthToken(userId, credential);
        Map<String, String> toReturn = findService(token, businessKey, serviceName, bindingKey);
        discardAuthToken(token);
        return toReturn;
    }

    public String getAuthToken(String userId, String credential) throws RemoteException {
        GetAuthToken getAuthToken = new GetAuthToken();
        getAuthToken.setUserID(userId);
        getAuthToken.setCred(credential);
        AuthToken authToken = security.getAuthToken(getAuthToken);
        System.out.println("Login successful!");

        return authToken.getAuthInfo();
    }

    public void discardAuthToken(String token) throws RemoteException {
        security.discardAuthToken(new DiscardAuthToken(token));
        System.out.println("Logged out");
    }

    public Map<String, String> findService(String token, String businessKey, String serviceKey, String bindingKey) throws Exception {
        BusinessList businessList = getBusinessList(token, businessKey, 0, 100);
        return printServiceDetailsByBusiness(businessList.getBusinessInfos(), serviceKey, bindingKey, token);
    }

    /**
     * Find all of the registered businesses. This list may be filtered
     * based on access control rules
     *
     * @param token
     * @return
     * @throws Exception
     */
    private BusinessList getBusinessList(String token, String query, int offset, int maxrecords) throws Exception {
        FindBusiness findBusiness = new FindBusiness();
        findBusiness.setAuthInfo(token);
        FindQualifiers findQualifiers = new org.uddi.api_v3.FindQualifiers();
        findQualifiers.getFindQualifier().add(UDDIConstants.APPROXIMATE_MATCH);

        findBusiness.setFindQualifiers(findQualifiers);
        Name searchName = new Name();
        if (query == null || query.equalsIgnoreCase("")) {
            searchName.setValue(UDDIConstants.WILDCARD);
        } else {
            searchName.setValue(query);
        }
        findBusiness.getName().add(searchName);
        findBusiness.setListHead(offset);
        findBusiness.setMaxRows(maxrecords);
        BusinessList businessList = inquiry.findBusiness(findBusiness);
        return businessList;

    }

    /**
     * Converts category bags of tmodels to a readable string
     *
     * @param categoryBag
     * @return
     */
    private String catBagToString(CategoryBag categoryBag) {
        StringBuilder sb = new StringBuilder();
        if (categoryBag == null) {
            return "no data";
        }
        for (int i = 0; i < categoryBag.getKeyedReference().size(); i++) {
            sb.append(keyedReferenceToString(categoryBag.getKeyedReference().get(i)));
        }
        for (int i = 0; i < categoryBag.getKeyedReferenceGroup().size(); i++) {
            sb.append("Key Ref Grp: TModelKey=");
            for (int k = 0; k < categoryBag.getKeyedReferenceGroup().get(i).getKeyedReference().size(); k++) {
                sb.append(keyedReferenceToString(categoryBag.getKeyedReferenceGroup().get(i).getKeyedReference().get(k)));
            }
        }
        return sb.toString();
    }

    private String keyedReferenceToString(KeyedReference item) {
        StringBuilder sb = new StringBuilder();
        sb.append("Key Ref: Name=").
                append(item.getKeyName()).
                append(" Value=").
                append(item.getKeyValue()).
                append(" tModel=").
                append(item.getTModelKey()).
                append(System.getProperty("line.separator"));
        return sb.toString();
    }

    private void fillServiceDetail(Map<String, String> toReturn, BusinessService get, String bindingKey) {
        if (get == null) {
            return;
        }

        System.out.println("=== Name   : " + listToString(get.getName()));
        toReturn.put("Name", listToString(get.getName()));
        System.out.println("=== Desc   : " + listToDescString(get.getDescription()));
        toReturn.put("Desc", listToDescString(get.getDescription()));
        System.out.println("=== Key    : " + (get.getServiceKey()));
        toReturn.put("Key", (get.getServiceKey()));
        System.out.println("=== Cat bag: " + catBagToString(get.getCategoryBag()));
        toReturn.put("CatBag", catBagToString(get.getCategoryBag()));
        if (!get.getSignature().isEmpty()) {
            System.out.println("=== Item is digitally signed");
            toReturn.put("Signed", "true");
        } else {
            System.out.println("=== Item is not digitally signed");
            toReturn.put("Signed", "false");
        }
        fillBindingTemplates(toReturn, get.getBindingTemplates(), bindingKey);
    }

    /**
     * This function is useful for translating UDDI's somewhat complex data
     * format to something that is more useful.
     *
     * @param toReturn
     * @param bindingTemplates
     */
    private void fillBindingTemplates(Map<String, String> toReturn, BindingTemplates bindingTemplates, String bindingKey) {
        if (bindingTemplates == null) {
            return;
        }
        for (int i = 0; i < bindingTemplates.getBindingTemplate().size(); i++) {
            System.out.println("====== Binding Key: " + bindingTemplates.getBindingTemplate().get(i).getBindingKey());
            if (bindingTemplates.getBindingTemplate().get(i).getBindingKey().equals(bindingKey)) {
                if (bindingTemplates.getBindingTemplate().get(i).getAccessPoint() != null) {
                    System.out.println("====== Access Point: " + bindingTemplates.getBindingTemplate().get(i).getAccessPoint().getValue() + " type " + bindingTemplates.getBindingTemplate().get(i).getAccessPoint().getUseType());
                    if (bindingTemplates.getBindingTemplate().get(i).getAccessPoint().getUseType() != null) {
                        if (bindingTemplates.getBindingTemplate().get(i).getAccessPoint().getUseType().equalsIgnoreCase(AccessPointType.WSDL_DEPLOYMENT.toString())) {
                            //System.out.println("Use this access point value as a URL to a WSDL document, which presumably will have a real access point defined.");
                            toReturn.put(VAR_WSDL_URI, bindingTemplates.getBindingTemplate().get(i).getAccessPoint().getValue());
                        }
                    }
                }
            }
        }
    }

    private String listToString(List<Name> name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.size(); i++) {
            sb.append(name.get(i).getValue()).append(" ");
        }
        return sb.toString();
    }

    private String listToDescString(List<Description> name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.size(); i++) {
            sb.append(name.get(i).getValue()).append(" ");
        }
        return sb.toString();
    }

    private Map<String, String> printServiceDetailsByBusiness(BusinessInfos businessInfos, String serviceKey, String bindingKey, String token) throws Exception {
        Map<String, String> toReturn = new HashMap<>();
        for (int i = 0; i < businessInfos.getBusinessInfo().size(); i++) {
            GetServiceDetail gsd = new GetServiceDetail();
            gsd.getServiceKey().add(serviceKey);
            gsd.setAuthInfo(token);
            System.out.println("Fetching data for business " + businessInfos.getBusinessInfo().get(i).getBusinessKey());
            ServiceDetail serviceDetail = inquiry.getServiceDetail(gsd);
            for (int k = 0; k < serviceDetail.getBusinessService().size(); k++) {
                fillServiceDetail(toReturn, serviceDetail.getBusinessService().get(k), bindingKey);
            }
        }

        return toReturn;
    }

    public static void main(String[] args) {
        try {
            SOAPLookupService instance = SOAPLookupService.getInstance();
            String businessName = "c2sense.org";

            String serviceKey = "uddi:c2sense.org:c2esb-service-manager";
            String bindingKey = "uddi:c2sense.org:c2esb-service-manager-binding-ws";
            Map<String, String> map = instance.lookup(businessName, serviceKey, bindingKey, "c2sense", "c2sense");
            for (String key: map.keySet()){
                System.out.println("########################");
                System.out.println("### key  : " + key);
                System.out.println("### value: " + map.get(key));
            }

            System.out.println("########################");
            System.out.println("" + VAR_WSDL_URI + ": " + map.get(VAR_WSDL_URI));

            serviceKey = "uddi:c2sense.org:ipgw-service";
            bindingKey = "uddi:c2sense.org:ipgw-service-binding-ws";
            map = instance.lookup(businessName, serviceKey, bindingKey, "c2sense", "c2sense");
            for (String key: map.keySet()){
                System.out.println("########################");
                System.out.println("### key  : " + key);
                System.out.println("### value: " + map.get(key));
            }

            System.out.println("########################");
            System.out.println("" + VAR_WSDL_URI + ": " + map.get(VAR_WSDL_URI));
        } catch (ServiceConfigurationException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
