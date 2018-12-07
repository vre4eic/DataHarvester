package gr.forth.ics.isl.exporter;

import gr.forth.ics.isl.common.D4ScienceResources;
import gr.forth.ics.isl.exception.GenericException;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import lombok.extern.log4j.Log4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * @author Yannis Marketakis (marketak 'at' ics 'dot' forth 'dot' gr)
 */
@Log4j
public class D4ScienceExporter {
    private String endpointUrl;
    
    public D4ScienceExporter(String endpointUrl){
        this.endpointUrl=endpointUrl;
    }
    
    public void exportAll() throws GenericException {
        Collection<String> allResourceIDs=this.fetchAllDatasetUUIDs();
        log.info("Found "+allResourceIDs.size()+" resources");
        for(String resourceId : allResourceIDs){
            try{
                this.exportToXml(this.transformToXml(this.exportResource(resourceId)), new File("output/"+resourceId+D4ScienceResources.EXTENSION_XML));
            }catch(GenericException ex){
                log.error("An error occured while exporting XML data",ex);
            }
            
        }
    }
    
    private Collection<String> fetchAllDatasetUUIDs() throws GenericException{
        log.info("Fetching the list of Resource IDs from the resource catalog");
        Set<String> retCollection=new HashSet<>();
        try{
            HttpURLConnection conn=(HttpURLConnection)new URL(this.endpointUrl+D4ScienceResources.ALL_RESOURCES_ENDPOINT).openConnection();
            BufferedReader br=new BufferedReader(new InputStreamReader(conn.getInputStream(),"UTF-8"));
            String line;
            StringBuilder sb=new StringBuilder();
            while((line=br.readLine())!=null){
                sb.append(line).append("\n");
            }
            br.close();
            JSONObject jsonResultObject=new JSONObject(sb.toString());
            JSONArray jsonResults=jsonResultObject.getJSONArray(D4ScienceResources.RESULT);
            for(int i=0;i<jsonResults.length();i++){
                retCollection.add(jsonResults.getString(i));
            }
            
        }catch(IOException ex){
            log.error("Cannot retrieve the list of resources",ex);
            throw new GenericException("Cannot retrieve the list of resources",ex);
        }
        return retCollection;
    }
    
    private JSONObject exportResource(String resourceId) throws GenericException{
        log.info("Fetching the contents of Resource with ID "+resourceId);
        try{
            String resourceUrl=this.endpointUrl+D4ScienceResources.RESOURCE_ENDPOINT+"?"+D4ScienceResources.ID_PARAMETER+"="+resourceId;
            log.debug("Fetching resource data from URL: "+resourceUrl);
            HttpURLConnection conn=(HttpURLConnection)new URL(resourceUrl).openConnection();
            BufferedReader br=new BufferedReader(new InputStreamReader(conn.getInputStream(),"UTF-8"));
            String line;
            StringBuilder sb=new StringBuilder();
            while((line=br.readLine())!=null){
                sb.append(line).append("\n");
            }
            br.close();
            return new JSONObject(sb.toString());
        }catch(IOException ex){
            log.error("An error occured while retrieving the resource with ID "+resourceId,ex);
            throw new GenericException("An error occured while retrieving the resource with ID "+resourceId,ex);
        }
    }
    
    private String transformToXml(JSONObject jsonObject){
        JSONObject realObject=jsonObject.getJSONObject("result");
        if(realObject.has("extras")){
            JSONArray extrasArray=realObject.getJSONArray("extras");
            if(extrasArray!=null){
                for(int i=0;i<extrasArray.length();i++){
                    if(extrasArray.getJSONObject(i).getString("key").equalsIgnoreCase("responsible-party")){
                        realObject.put("responsible_party", new JSONArray(extrasArray.getJSONObject(i).getString("value")));
                    }else if(extrasArray.getJSONObject(i).getString("key").equalsIgnoreCase("dataset-reference-date")){
                        realObject.put("dataset-reference-date", new JSONArray(extrasArray.getJSONObject(i).getString("value")));
                    }
                }
            }
        }
        return D4ScienceResources.RESOURCE_ELEMENT_OPEN+"\n"
               + XML.toString(jsonObject)+"\n"
               + D4ScienceResources.RESOURCE_ELEMENT_CLOSE+"\n";
    }
    
    
    public void exportToXml(String xmlContents, File xmlFile) throws GenericException{
        try{
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document doc=builder.parse(new InputSource(new ByteArrayInputStream(xmlContents.getBytes("UTF-8"))));
            TransformerFactory transformerFactory=TransformerFactory.newInstance();
            Transformer transformer=transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            DOMSource source=new DOMSource(doc);
            StreamResult result=new StreamResult(xmlFile);
            transformer.transform(source, result);
        }catch(IOException | ParserConfigurationException | SAXException | TransformerException ex){
            log.error("An error occured while exporting the XML contents",ex);
            throw new GenericException("An error occured while exporting the XML contents",ex);
        }
    }
        
    public static void main(String[] args) throws GenericException{
        new D4ScienceExporter(D4ScienceResources.D4SCIENCE_CATALOG_URL).exportAll();
    }
}