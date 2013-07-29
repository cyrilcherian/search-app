/**
 * 
 */
package org.qburst.search.controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.codehaus.jackson.map.ObjectMapper;
import org.qburst.search.indexer.AbstractSearchIndexer;
import org.qburst.search.model.Search;
import org.qburst.search.model.UploadStatus;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.DefaultMultipartHttpServletRequest;

/**
 * @author Cyril
 * 
 */
@Controller
public class SearchController {

	public SearchController() {

	}

	@RequestMapping(value = "/search", method = RequestMethod.GET)
	public @ResponseBody
	String doSearch(
			@RequestParam(required = true, value = "query") String q) {
		String jsonData = "";
		try {
			HttpSolrServer solr = new HttpSolrServer(
					"http://10.4.0.56:8983/solr/books");
			SolrQuery query = new SolrQuery();
			query.setQuery(q);
			query.setFields("content", "author", "url", "title");
			query.setHighlight(true);
			query.addHighlightField("content");
			query.setHighlightSnippets(1000);
			query.setHighlightSimplePost("</span>");
			query.setHighlightSimplePre("<span class='label label-important'>");
			QueryResponse response = solr.query(query);
			SolrDocumentList results = response.getResults();
			Map<String, Map<String, List<String>>> highlights = response
					.getHighlighting();
			ArrayList<Search> mySearch = new ArrayList<Search>();
			int idx = 0;
			for (String key : highlights.keySet()) {
				List<String> data = highlights.get(key).get("content");
				if (data != null) {
					Search s = new Search();
					s.setHighlights(data);
					s.setId(key);
					s.setAuthor(results.get(idx).containsKey("author") ? stringify(results
							.get(idx).get("author")) : "Unknown");
					s.setUrl(results.get(idx).containsKey("url") ? results
							.get(idx).get("url").toString() : "");
					s.setTitle(results.get(idx).containsKey("title") ? stringify(results
							.get(idx).get("title")) : "");
					mySearch.add(s);
				}
				idx++;
			}
			ObjectMapper mapper = new ObjectMapper();
			jsonData = mapper.writeValueAsString(mySearch);
			solr.shutdown();
		} catch (Exception e) {
			e.printStackTrace();
		}
		String a = "aaData";
		return "{" + '"' + a + '"' + ":" + jsonData + "}";
	}

	private String stringify(Object ary) {
		String ret = "";
		if (ary != null && ary instanceof ArrayList) {
			ArrayList objects = (ArrayList) ary;
			ret = StringUtils.arrayToDelimitedString(objects.toArray(), ", ");
		}
		return ret;
	}
	@RequestMapping(value = "/export", method = RequestMethod.GET)
	public void doDownload(HttpServletRequest request,HttpServletResponse response,@RequestParam(required = true, value = "filePath") String filePath)
			throws IOException {
		int BUFFER_SIZE = 4096;
		ServletContext context = request.getServletContext();
		File downloadFile = new File(filePath);
		if(downloadFile.exists()){
			String appPath = context.getRealPath("");
			String fullPath = appPath + filePath;
			FileInputStream inputStream = new FileInputStream(downloadFile);
			String mimeType = context.getMimeType(fullPath);
			if (mimeType == null) {
				mimeType = "application/pdf";
			}
			response.setContentType(mimeType);
			response.setContentLength((int) downloadFile.length());
			String headerKey = "Content-Disposition";
			String headerValue = String.format("attachment; filename=\"%s\"",downloadFile.getName());
			response.setHeader(headerKey, headerValue);
			OutputStream outStream = response.getOutputStream();
			byte[] buffer = new byte[BUFFER_SIZE];
			int bytesRead = -1;
			while ((bytesRead = inputStream.read(buffer)) != -1) {
				outStream.write(buffer, 0, bytesRead);
			}
			inputStream.close();
			outStream.close();
		}
	}
	
	@RequestMapping(value="/upload", method=RequestMethod.POST)
	public @ResponseBody String upload(@RequestParam("files[]") ArrayList<MultipartFile> files, HttpServletRequest request) {
		ArrayList<UploadStatus> usList = new ArrayList<UploadStatus>();
		String jsonData = "";
		for (MultipartFile mf : files){
			String fn = mf.getOriginalFilename();
			fn = AbstractSearchIndexer.home_folder + "/" + mf.getOriginalFilename();
			File file = new File(fn);
			UploadStatus us = new UploadStatus();
			us.setFileName(mf.getOriginalFilename());
			if (!file.exists()){
				try{
					FileOutputStream fos = new FileOutputStream(file);
					fos.write(mf.getBytes());
					fos.close();
					us.setStatus("SUCCESS");
				} catch (Exception e){
					e.printStackTrace();
					us.setStatus("FAILURE");
					us.setReason(e.getMessage());
				}
			} else {
				us.setStatus("FAILURE");
				us.setReason("File Already Present");
			}
			usList.add(us);
		}
		ObjectMapper mapper = new ObjectMapper();
		try {
			jsonData = mapper.writeValueAsString(usList);
		} catch(Exception e) {
			e.printStackTrace();
		}
		return jsonData;
	}
}
