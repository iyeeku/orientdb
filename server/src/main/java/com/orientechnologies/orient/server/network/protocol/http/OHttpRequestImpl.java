package com.orientechnologies.orient.server.network.protocol.http;

import com.orientechnologies.orient.core.config.OContextConfiguration;
import com.orientechnologies.orient.core.metadata.security.OToken;
import com.orientechnologies.orient.server.network.protocol.ONetworkProtocolData;
import com.orientechnologies.orient.server.network.protocol.http.multipart.OHttpMultipartBaseInputStream;

import java.io.InputStream;
import java.util.Map;

public class OHttpRequestImpl extends OHttpRequest {

  private String                        content;
  private String                        sessionId;
  private String                        url;
  private Map<String, String>           parameters;
  private String                        httpMethod;
  private String                        httpVersion;
  private String                        contentType;
  private String                        contentEncoding;
  private OHttpMultipartBaseInputStream multipartStream;
  private String                        boundary;
  private boolean                       isMultipart;
  private String                        ifMatch;
  private String                        authentication;
  private boolean                       keepAlive = true;
  private Map<String, String>           headers;
  private String                        bearerTokenRaw;
  private OToken                        bearerToken;

  public OHttpRequestImpl(ONetworkProtocolHttpAbstract iExecutor, InputStream iInStream, ONetworkProtocolData iData,
      OContextConfiguration iConfiguration) {
    super(iExecutor, iInStream, iData, iConfiguration);
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }



  @Override
  public String getSessionId() {
    return sessionId;
  }

  @Override
  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  @Override
  public void setUrl(String url) {
    this.url = url;
  }

  @Override
  public void setParameters(Map<String, String> parameters) {
    this.parameters = parameters;
  }

  @Override
  public String getHttpMethod() {
    return httpMethod;
  }

  @Override
  public void setHttpMethod(String httpMethod) {
    this.httpMethod = httpMethod;
  }

  @Override
  public String getHttpVersion() {
    return httpVersion;
  }

  @Override
  public void setHttpVersion(String httpVersion) {
    this.httpVersion = httpVersion;
  }

  @Override
  public String getContentType() {
    return contentType;
  }

  @Override
  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  @Override
  public String getContentEncoding() {
    return contentEncoding;
  }

  @Override
  public void setContentEncoding(String contentEncoding) {
    this.contentEncoding = contentEncoding;
  }

  @Override
  public OHttpMultipartBaseInputStream getMultipartStream() {
    return multipartStream;
  }

  @Override
  public void setMultipartStream(OHttpMultipartBaseInputStream multipartStream) {
    this.multipartStream = multipartStream;
  }

  @Override
  public String getBoundary() {
    return boundary;
  }

  @Override
  public void setBoundary(String boundary) {
    this.boundary = boundary;
  }

  @Override
  public boolean isMultipart() {
    return isMultipart;
  }

  @Override
  public void setMultipart(boolean multipart) {
    isMultipart = multipart;
  }

  @Override
  public String getIfMatch() {
    return ifMatch;
  }

  @Override
  public void setIfMatch(String ifMatch) {
    this.ifMatch = ifMatch;
  }

  @Override
  public String getAuthentication() {
    return authentication;
  }

  @Override
  public void setAuthentication(String authentication) {
    this.authentication = authentication;
  }

  @Override
  public boolean isKeepAlive() {
    return keepAlive;
  }

  @Override
  public void setKeepAlive(boolean keepAlive) {
    this.keepAlive = keepAlive;
  }

  @Override
  public void setHeaders(Map<String, String> headers) {
    this.headers = headers;
  }

  @Override
  public String getBearerTokenRaw() {
    return bearerTokenRaw;
  }

  @Override
  public void setBearerTokenRaw(String bearerTokenRaw) {
    this.bearerTokenRaw = bearerTokenRaw;
  }

  @Override
  public OToken getBearerToken() {
    return bearerToken;
  }

  @Override
  public void setBearerToken(OToken bearerToken) {
    this.bearerToken = bearerToken;
  }

  @Override
  public Map<String, String> getParameters() {
    return parameters;
  }

  @Override
  public Map<String, String> getHeaders() {
    return headers;
  }

  @Override
  public String getUrl() {
    return url;
  }
}
