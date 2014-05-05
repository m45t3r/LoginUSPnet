package br.usp.ime.thiagoko.loginuspnet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.xml.sax.InputSource;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;
import br.usp.ime.thiagoko.http.HttpUtils;
import br.usp.ime.thiagoko.loginuspnet.Constants.Networks;

public class WifiChangeReceiver extends BroadcastReceiver {

	private static final String TAG = WifiChangeReceiver.class.getSimpleName();

	Context context = null;

	@Override
	public void onReceive(Context context, Intent intent) {
		final String action = intent.getAction();
		this.context = context;

		Log.v(TAG, "Action: " + action);

		if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
			NetworkInfo info = (NetworkInfo) intent
					.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
			if (info.getDetailedState() == DetailedState.CONNECTED) {
				Log.v(TAG, "Connected");

				WifiManager wifiManager = (WifiManager) context
						.getSystemService(Context.WIFI_SERVICE);
				WifiInfo wifiInfo = wifiManager.getConnectionInfo();

				String result = null;
				if (wifiInfo.getSSID().toUpperCase().contains(Networks.USPNET)) {
					result = Networks.USPNET;
				} else if (wifiInfo.getSSID().toUpperCase().contains(Networks.HCRP)) {
					result = Networks.USPNET;
				} else if (wifiInfo.getSSID().toUpperCase().contains(Networks.ICMC)) {
					result = Networks.ICMC;
				}

				if (result != null) {
					Log.d(TAG, result + " network detected.");
					new loginThread().execute(result);
				} else {
					Log.d(TAG, "No known network detected.");
				}
			}

		}
	}

	/**
	 * Send a POST request to httpURL with arguments passed to nvps
	 * 
	 * @param httpsURL
	 * @param nvps
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	private void sendRequest(String httpsURL, List<BasicNameValuePair> nvps, String redirURL)
			throws ClientProtocolException, IOException
	{

		HttpClient client = HttpUtils.getNewHttpClient();
		HttpPost httppost = new HttpPost(httpsURL);

		// Authentication block
		UrlEncodedFormEntity p_entity;

		p_entity = new UrlEncodedFormEntity(nvps, HTTP.UTF_8);
		httppost.setEntity(p_entity);
		// Send request and get response
		HttpResponse response = client.execute(httppost);
		HttpEntity responseEntity = response.getEntity();

		// Processing response
		InputSource inputSource = new InputSource(responseEntity.getContent());
		BufferedReader in = 
				new BufferedReader(new InputStreamReader(inputSource.getByteStream()));

		String inputLine;
		while ((inputLine = in.readLine()) != null) {
			// Checking response page, can be used to verify authentication
			// success
			if(inputLine.toLowerCase().contains(redirURL.toLowerCase())) {
				Log.d(TAG, "Connection successful!");
				break;
			}
		}
	}

	/**
	 * Get USPNet's login page by trying to access a page without authentication
	 * 
	 * @return USPNet's login page
	 */
	private URL findPostURL() {

		// Any page to check redirect
		final String path = "http://ime.usp.br";
		String resultPage = "";
		String resultURL = "";
		URL result;
		URL url;
		URLConnection con;
		final char[] buffer;
		StringBuilder out;
		Reader in;
		int urlIndex;

		try {
			url = new URL(path);
			con = url.openConnection();
			trustAllCertificates();

			// Tries to connect to a page and store return on String.
			// If this is the USPNet's login page store it's URL

			buffer = new char[0x10000];
			out = new StringBuilder();
			in = new InputStreamReader(openConnectionCheckRedirects(con), "UTF-8");
			int read;

			do {
				read = in.read(buffer, 0, buffer.length);
				if (read > 0) {
					out.append(buffer, 0, read);
				}
			} while (read >= 0);

			resultPage = out.toString();

			urlIndex = resultPage.toUpperCase().lastIndexOf("ACTION=\"");
			if (urlIndex != -1) {
				urlIndex += 8;
				
				for (; urlIndex < resultPage.length() && resultPage.charAt(urlIndex) != '"';
						urlIndex++)
				{
					resultURL += resultPage.charAt(urlIndex);
				}
				
			} else {
				resultURL = "";
			}

		} catch (MalformedURLException e) {
			Log.e("MalformedURLException", "Message: " + e.getMessage());
		} catch (SocketTimeoutException e) {
			Log.e("SocketTimeoutException", "Message: " + e.getMessage());
		} catch (IOException e) {
			Log.e("IOExceptionNet", "Message: " + e.getMessage());
		} catch (KeyManagementException e) {
			Log.e("KeyManagementException", "Message: " + e.getMessage());
		} catch (NoSuchAlgorithmException e) {
			Log.e("NoSuchAlgorithmException", "Message: " + e.getMessage());
		}
		Log.d(TAG, "USPNet's login URL: " + resultURL);
		
		// Validate returned URL
		try {
			result = new URL(resultURL);
		} catch (MalformedURLException e) {
			result = null;
			Log.e("MalformedURLException", "Message: " + e.getMessage());
		}

		return result;
	}

	/**
	 * Open connection and accept http to https redirection Used to get USPNet's
	 * login page
	 * 
	 * @param c
	 * @return
	 * @throws IOException
	 */
	private InputStream openConnectionCheckRedirects(URLConnection c) throws IOException {
		
		boolean redir;
		int redirects = 0;
		InputStream in = null;

		do {
			if (c instanceof HttpURLConnection) {
				((HttpURLConnection) c).setInstanceFollowRedirects(false);
			}
			
			// We want to open the input stream before getting headers
			// because getHeaderField() et al swallow IOExceptions.
			in = c.getInputStream();
			redir = false;
			
			if (c instanceof HttpURLConnection) {
				
				HttpURLConnection http = (HttpURLConnection) c;
				int stat = http.getResponseCode();
				
				if (stat >= 300 && stat <= 307 && stat != 306
						&& stat != HttpURLConnection.HTTP_NOT_MODIFIED)
				{
					URL base = http.getURL();
					String loc = http.getHeaderField("Location");
					URL target = null;
					
					if (loc != null) {
						target = new URL(base, loc);
					}
					
					http.disconnect();
				
					// Redirection should be allowed only for HTTP and HTTPS
					// and should be limited to 5 redirections at most.
					if (target == null
							|| !(target.getProtocol().equals("http") || target
									.getProtocol().equals("https"))
							|| redirects >= 5) {
						throw new SecurityException("illegal URL redirect");
					}
					
					redir = true;
					c = target.openConnection();
					redirects++;
				}
			}
		
		} while (redir);

		return in;
	}

	/**
	 * Disable SSL checks USPnet's page doesn't have a valid certificate
	 * 
	 * @throws NoSuchAlgorithmException
	 * @throws KeyManagementException
	 */
	private void trustAllCertificates() throws NoSuchAlgorithmException, KeyManagementException {
		
		// Create a trust manager that does not validate certificate chains
		TrustManager[] trustAllCerts = new TrustManager[] {
				new X509TrustManager() {
					
					public java.security.cert.X509Certificate[] getAcceptedIssuers() {
						return null;
					}

					public void checkClientTrusted(X509Certificate[] certs, String authType) {}

					public void checkServerTrusted(X509Certificate[] certs, String authType) {}
				
				}
		};

		// Install the all-trusting trust manager
		SSLContext sc = SSLContext.getInstance("SSL");
		sc.init(null, trustAllCerts, new java.security.SecureRandom());
		HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

		// Create all-trusting host name verifier
		HostnameVerifier allHostsValid = new HostnameVerifier() {
			public boolean verify(String hostname, SSLSession session) {
				return true;
			}
		};

		// Install the all-trusting host verifier
		HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
	}

	/**
	 * Do login on a separate thread to not block main thread
	 */
	private class loginThread extends AsyncTask<String, Void, Void> {

		@Override
		protected Void doInBackground(String... id) {

			SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
			final List<BasicNameValuePair> nvps = new ArrayList<BasicNameValuePair>();
			final String redirURL = "https://www.google.com";

			if (id[0].equals(Networks.USPNET)) {

				final URL httpsURL = findPostURL();

				if (httpsURL != null) {
					nvps.add(new BasicNameValuePair("zone", "uspnet"));
					nvps.add(new BasicNameValuePair("redirurl", redirURL));
					nvps.add(new BasicNameValuePair("auth_user",
							preferences.getString(context.getString(R.string.pref_username), "")));
					nvps.add(new BasicNameValuePair("auth_pass",
							preferences.getString(context.getString(R.string.pref_password), "")));
					nvps.add(new BasicNameValuePair("accept", "Acessar"));

					try {
						sendRequest(httpsURL.toString(), nvps, redirURL);
					} catch (ClientProtocolException e) {
						Log.e(TAG, "ClientProtocolException while connecting to "
								+ id[0] + " Message: " + e.getMessage());
						Log.e(TAG, Log.getStackTraceString(e));
					} catch (IOException e) {
						Log.e(TAG, "IOException while connecting to "
								+ id[0] + " Message: " + e.getMessage());
						Log.e(TAG, Log.getStackTraceString(e));
					}
				} else {
					Log.d(TAG, "Already authenticate to USPnet network");
				}

			} else if (id[0].equals(Networks.ICMC)) {
				
				final String httpsURL = "https://1.1.1.1/login.html?redirect=" + redirURL;

				nvps.add(new BasicNameValuePair("buttonClicked", "4"));
				nvps.add(new BasicNameValuePair("err_flag", "0"));
				nvps.add(new BasicNameValuePair("err_msg", ""));
				nvps.add(new BasicNameValuePair("info_flag", "0"));
				nvps.add(new BasicNameValuePair("info_msg", ""));
				nvps.add(new BasicNameValuePair("redirect_url", redirURL));
				nvps.add(new BasicNameValuePair("username", 
						preferences.getString(context.getString(R.string.pref_username),"")));
				nvps.add(new BasicNameValuePair("password",
						preferences.getString(context.getString(R.string.pref_password),"")));

				try {
					sendRequest(httpsURL, nvps, redirURL);
				} catch (ClientProtocolException e) {
					Log.e(TAG, "ClientProtocolException while connecting to "
							+ id[0] + " Message: " + e.getMessage());
					Log.e(TAG, " " + Log.getStackTraceString(e));
				} catch (IOException e) {
					Log.e(TAG, "IOException while connecting to "
							+ id[0] + " Message: " + e.getMessage());
					Log.e(TAG, " " + Log.getStackTraceString(e));
				}

			}

			return null;
		}
	}

}
