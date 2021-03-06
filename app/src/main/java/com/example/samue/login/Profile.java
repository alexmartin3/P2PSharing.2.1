package com.example.samue.login;

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.net.ConnectivityManager;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
//import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.pubnub.api.Callback;
import com.pubnub.api.Pubnub;
import com.pubnub.api.PubnubException;
import com.tom_roush.pdfbox.multipdf.Splitter;
import com.tom_roush.pdfbox.pdmodel.PDDocument;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoRendererGui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import me.kevingleason.pnwebrtc.PnPeer;
import me.kevingleason.pnwebrtc.PnRTCClient;
import me.kevingleason.pnwebrtc.PnRTCListener;
import util.Constants;

@SuppressWarnings("unchecked")
public class Profile extends AppCompatActivity {
	public static DatabaseHelper mDatabaseHelper;
	private Dialog mdialog;
	private EditText name;
	private ListView friends_list;
	private FriendsAdapter adapter;
	private ArrayList<Friends> al_friends;
	private ArrayList<Friends> al_blocked_users;
	private String selectedFolder = null;
	private static final int BLOCKED_USERS_REQUEST = 4;
	private static final int SEE_SHARED_FOLDERS_REQUEST = 5;
	// Nombre de las carpetas, lista de archivos de cada una.
	private HashMap<String,ArrayList<String>> sharedFolders;
	// Nombre de las carpetas, lista de amigos que tienen acceso a cada una.
	private HashMap<String,ArrayList<String>> foldersAccess;
	private ArchivesDatabase mArchivesDatabase;
	private String userRecursos;

	private static final String LOCAL_MEDIA_STREAM_ID = "localStreamPN";
	private static final String LOCAL_MEDIA_STREAM_ID_SENDER = "localStreamPNsender";
	private static final String LOCAL_MEDIA_STREAM_ID_DOWNLOADER = "localStreamPNdownloader";
	private static PnRTCClient pnRTCClient;
	private PnRTCClient senderClient;
	static PnRTCClient downloaderClient;
	private Pubnub mPubNub;
	private Cryptography userPubNub;
	private String username;
	private FileSender activeFileSender;
	private SendersManager sendersManager;
	private boolean sendingFile;
	private DownloadService downloadService;
	private Intent dl_intent;
	private boolean mobileDataBlocked;
	private static ArrayList<Groups> listgroups;
	private ArrayList<Groups> newgroups;
	private static final String SENDTO = "sendTo";
	private static final String NAMEGROUP = "nameGroup";
	private static final String LISTFILES = "listFiles";
	private static final String LISTOWNERS = "listOwners";

	private Cryptography rsaUser;

	private ServiceConnection serviceConnection = new ServiceConnection(){
		@Override
		public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
			DownloadService.DownloadBinder binder = (DownloadService.DownloadBinder) iBinder;
			downloadService = binder.getService();
		}
		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			Log.e("ERROR EN DESCARGA", "SERVICIO DESCONECTADO INESPERADAMENTE");
		}
	};
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_profile);
		sendingFile = false;
		mobileDataBlocked = false;
		this.username = getIntent().getExtras().getString("user");
		al_blocked_users = new ArrayList<>();
        mDatabaseHelper = new DatabaseHelper(this);
        mArchivesDatabase = new ArchivesDatabase(this);
		loadBlockedUsersList();
		loadSharedFolders();
		loadFoldersAccess();
		friends_list = findViewById(R.id.friends_list);
		sendersManager = SendersManager.getSingleton();
		sendersManager.start();
		initUser();

		loadGroupList();
		populateListView();

		friends_list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				final String connectTo = al_friends.get(position).getNombre();
				mdialog = new Dialog(Profile.this);
				mdialog.setContentView(R.layout.dialog_friend_options);
				mdialog.show();

				Button deleteButton = mdialog.findViewById(R.id.deleteButton);
				Button seeFilesButton = mdialog.findViewById(R.id.seefriendfilesButton);
				Button seeFriendSFButton = mdialog.findViewById(R.id.seefriendSFButton);
				Button blockFriendButton = mdialog.findViewById(R.id.blockFriendButton);
				// Ver archivos compartidos por el amigo seleccionado.
				seeFilesButton.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						mdialog.dismiss();
						userRecursos = connectTo;
						Toast.makeText(getApplicationContext(), "Conectando con "+connectTo, Toast.LENGTH_LONG).show();
						publish(connectTo,"VAR","",false);
					}
				});
				// Borrar amigo.
				deleteButton.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						mdialog.dismiss();
						mDatabaseHelper.deleteFriend(connectTo);
						loadFoldersAccess();
						populateListView();
						Toast.makeText(getApplicationContext(), "Amigo "+ connectTo + " eliminado", Toast.LENGTH_LONG).show();
					}
				});
				// Ver carpetas compartidas con este dispositivo por el amigo seleccionado.
				seeFriendSFButton.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						mdialog.dismiss();
						userRecursos = connectTo;
						Toast.makeText(getApplicationContext(), "Conectando con "+connectTo, Toast.LENGTH_LONG).show();
						publish(connectTo, "VSF","",false); //View Shared Folders
					}
				});
				// Bloquear amigo.
				blockFriendButton.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						mdialog.dismiss();
						boolean inserted = mDatabaseHelper.addData(connectTo, DatabaseHelper.BLOCKED_TABLE_NAME);
						if (inserted) {
							mDatabaseHelper.deleteFriend(connectTo);
							loadFoldersAccess();
							loadBlockedUsersList();
							populateListView();
							Toast.makeText(getApplicationContext(), "Amigo " + connectTo + " bloqueado", Toast.LENGTH_LONG).show();
						}
					}
				});
			}
		});
		Toolbar myToolbar = findViewById(R.id.my_toolbar);
		setSupportActionBar(myToolbar);
		Objects.requireNonNull(getSupportActionBar()).setTitle(username + " - Amigos");
		//getSupportActionBar().setTitle("Hola, " + getIntent().getExtras().getString("user"));
		comprobarPermisos();
		// Botón para ir a la activity de grupos.
		FloatingActionButton groups = findViewById(R.id.groupsButton);
		groups.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
/*
				//PRUEBA PARA  CIFRAR Y FIRMAR INFORMACION
				try{
					String prueba = "prueba de cifrado con la secretkey que se genera en user2 se cifra con public de user1 y luego se firma con privada de user2";
					Cryptography user1 = new Cryptography();
					user1.genKeyPair();
					Cryptography user2 = new Cryptography();
					user2.genKeyPair();
					Cryptography userTemp = new Cryptography();

					byte[] signature = user1.signRSA(prueba);
					Log.i("ALEXXX-SIGN",user1.bytesToString(signature));
					boolean verify = user1.verifyRSA(signature,prueba);
					Log.i("ALEXXX-VERIFY", String.valueOf(verify));

					//SIMULACRO DE ENVIO DE CLAVE PUBLICA, GENERACION DE CLAVE SECRETA, CIFRADO CON CLAVE SECRETA
					//DESPUES LA CLAVE SECRETA SE FIRMA CON CLAVE PRIVADA DE USER1 Y SE ENVIA JUNTO CON LA CLAVE SECRETA CIFRADA
					// ENVIADO DE CLAVE SECRETA CIFRADA CON PUBLICA Y FIRMADA CON PRIVADA Y TEXTO CIFRADO, CONFIRMADO FIRMA DE
					// CLAVE SECRETA CIFRADA CON PUBLICA DE USER2 Y DESCIFRADO DE CLAVE SECRETA Y MENSAJE
					//primero sacamos publica de user 1 a string y enviamos a user 2 que guarda
					//ciframos con publica de 1, desciframos con privada de 1 y vemos resultado
					String pubkey =user1.getPublicKeyString();
					Log.i("TEST PUBLIC-1",pubkey); 		//muestro clave publica string
					String pubkeysended = pubkey;		//recibe clave publica string
					userTemp.setPublicKeyString(pubkeysended);		//añaddo la publica recibida y que usare
					Log.i("TEST PUBLIC-2",userTemp.getPublicKeyString());//igual 1
					userTemp.generateKey();	//genero en user2 clave secreta
					String secretKey = userTemp.getSecretKeyString();	//guardo la clave secreta para enviarla y usarla
					Log.i("TEST PUBLIC-3",secretKey); 			//muesto la clave secreta
					Log.i("TEST PUBLIC-3", String.valueOf(secretKey.length())); //muestro tamaño clave secreta
					String secretKeyCifrada = userTemp.cipherRSA(secretKey); 	//cifro la clave secreta con la publica para enviarla
					Log.i("TEST PUBLIC-4",secretKeyCifrada); 			//muesto la clave secreta cifrada con la publica
					Log.i("TEST PUBLIC-4", String.valueOf(secretKeyCifrada.length())); //muestro tamaño clave secreta cifrada
					String secretKeyCifyfirm = user2.signRSA(secretKeyCifrada);		//firmo la clave secreta cifrada con la privada de user2
					Log.i("TEST PUBLIC-4", secretKeyCifyfirm); //muestro la calve secreta cifrada y firmanda
					Log.i("TEST CIFRADO0",prueba);		//muestro mensaje original
					Log.i("TEST CIFRADO0", String.valueOf(prueba.length())); //muestro tamaño mensaje
					String pruebaCifrada = userTemp.cipherSimetric(prueba); 	//cifro el mensaje con la clave secreta
					Log.i("TEST CIFRADO1",pruebaCifrada);				//muestro el mensaje cifrado
					Log.i("TEST CIFRADO1", String.valueOf(pruebaCifrada.length()));//muestro tamaño mensaje cifrado
					String pruebaRecibida = pruebaCifrada;			//envio mensaje cifrado a user 1 y guarda
					userTemp.setPublicKey(user2.getPublicKey());
					String secretKeyCifradaRecibida = secretKeyCifrada;		//recibe la clave secreta encriptada
					String secretKeyCifyfirmRecibida = secretKeyCifyfirm;	//recibe la clave secreta encriptada y firmada
					boolean verificacion = userTemp.verifyRSA(secretKeyCifyfirmRecibida,secretKeyCifradaRecibida);//verifica que la clave cifrada ez igual que la firmada, para ver que es de la misma persona y no se ha modificado
					Log.i("TEST CIFRADO2", String.valueOf(verificacion)); //muestro el resultado de la verificacion
					String secretKeyDescifrada = user1.decipherRSAToString(secretKeyCifradaRecibida); //descifro clave secreta cifrada
					Log.i("TEST CIFRADO2",secretKeyDescifrada);			//muestro la clave secreta desencriptada -- igual PUBLIC-3
					user1.setSecretKeyString(secretKeyDescifrada);			//guardo la clave secreta en el user- lista para usar
					String pruebaDescifrada = user1.decipherSimetricToString(pruebaRecibida); //descifro el mensaje con la clave secreta
					Log.i("TEST CIFRADO3",pruebaRecibida);		//igual que CIFRADO 1
					Log.i("TEST CIFRADO4",pruebaDescifrada);		//igual que CIFRADO 0
					Log.i("TEST CIFRADO4", String.valueOf(pruebaDescifrada.length())); //muestro tamaño mensaje


				//cifrar API KEYS de pubnub
				Cryptography rsa = new Cryptography();
				try {
					rsa.generateKey();
					String clave1Cifrada = rsa.cipherSimetric(Constants.PUB_KEY);
					Log.i("PUB_KEY-CLAVECIFRADA",clave1Cifrada);
					Log.i("PUB_KEY-CLAVESECRETA",rsa.getSecretKeyString());
					String clave2Cifrada = rsa.cipherSimetric(Constants.SUB_KEY);
					Log.i("PUB_SUB-CLAVECIFRADA",clave2Cifrada);
					Log.i("PUB_SUB-CLAVESECRETA",rsa.getSecretKeyString());


				}catch(Exception e){
					e.printStackTrace();
				}
*/


				Intent intent = new Intent(Profile.this, listGroupsActivity.class);
				intent.putExtra("username", username);
				startActivityForResult(intent, 6);


			}
		});
		FloatingActionButton fab = findViewById(R.id.addFriendsFAB);
		// Botón para compartir un archivo o una carpeta.
		fab.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(Profile.this, ArchiveExplorer.class);
				intent.putExtra("friendsList", al_friends);
				startActivityForResult(intent, 1);
			}
		});
		try {
			initPubNub();
		} catch (Exception e) {

		}
		// Arranque del servicio de descargas.
		dl_intent = new Intent(this, DownloadService.class);
		startService(dl_intent);
		boolean serviceBound = bindService(dl_intent, serviceConnection, BIND_AUTO_CREATE);
	}

	private void publish(final String connectTo, final String connectionType, final String nameGroup, final Boolean preview){
		String userCall = connectTo + Constants.STDBY_SUFFIX;
		JSONObject jsonCall = new JSONObject();
		try {
			jsonCall.put(Constants.JSON_CALL_USER, username);
			mPubNub.publish(userCall, jsonCall, new Callback() {
				@Override
				public void successCallback(String channel, Object message) { //conectamos nosotros al otro
					Log.d("MA-dCall", "SUCCESS: " + message.toString());
					try {
						connectPeer(connectTo, true); //conectamos con el peer
					/* Parada necesaria para asegurar que se realiza bien la conexión, ya sea para esperar
					 * el tráfico en caso de estar la red congestionada o bien para esperar la respuesta desde
					 * un dispositivo lento.
					 */
						Thread.sleep(1500);
					}
					catch (Exception e) {}

					if(connectionType.equals("VAR")){ //buscamos que tipo de mensaje debemos enviar
						VAR(connectTo);
					}else if(connectionType.equals("FR")){
						FR(connectTo);
					}else if(connectionType.equals("VSF")){
						VSF(connectTo);
					}else if(connectionType.equals("NG")){
						NG(connectTo,nameGroup);
					}else if(connectionType.equals("DG")){
						DG(connectTo,nameGroup);
					}else if(connectionType.equals("RA")){
						RA(nameGroup,connectTo,preview,true);
					}
				}
			});
		} catch (JSONException e) {

		}
	}
	private void comprobarPermisos(){
		if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
			ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
		}
	}
	private void initPubNub() throws Exception {
		String stdbyChannel = this.username + Constants.STDBY_SUFFIX;
		this.mPubNub = new Pubnub(userPubNub.pubnub(Constants.PUB_KEY), userPubNub.pubnub(Constants.SUB_KEY));
		this.mPubNub.setUUID(this.username);
		try {
			this.mPubNub.subscribe(stdbyChannel, new Callback(){ //creamos nuestro canal y nos quedamos en stand-by esperando alguna conexión
				@Override
				public void successCallback(String channel, Object message) { //despierta cuando alguien se conecta a nuestro canal y responde con ACK
					Log.v("MA-success", "MESSAGE: " + message.toString());
					if (!(message instanceof JSONObject)) return; // Ignore if not JSONObject
					JSONObject jsonMsg = (JSONObject) message;
					try {
						if (!jsonMsg.has(Constants.JSON_CALL_USER)) return;
						connectPeer("", false);
					} catch (Exception e) {

					}
				}
			});
		} catch (PubnubException e) {

		}
	}
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch(requestCode){
			case 1:
				if(resultCode == Activity.RESULT_OK){
					// Si se ha compartido una carpeta hay que volver a cargar las carpetas y lista de acceso de la BD...
					boolean isFolderSharing = data.getBooleanExtra("folder_sharing", false);
					if (isFolderSharing){
						loadSharedFolders();
						loadFoldersAccess();
					}
					// Si no se ha compartido una carpeta entonces se ha compartido un solo archivo:
					else {
						String name = data.getStringExtra("name");
						String path = data.getStringExtra("path");

						if (!this.mArchivesDatabase.exists(name)) {
							this.mArchivesDatabase.addData(name, path);
							notificate("Se ha compartido el archivo");
						} else {
							notificate("El archivo ya estaba compartido");
						}
					}
				}
				break;
			case 2:
				if(resultCode == Activity.RESULT_OK){
					final String name = data.getStringExtra("name");
					String sendTo = data.getStringExtra(SENDTO);
					boolean isPreview = data.getBooleanExtra(Utils.REQ_PREVIEW, false);
					RA(name, sendTo, isPreview,false);
				}
				break;
			case 3:
				if(resultCode == Activity.RESULT_OK){
					final String name = data.getStringExtra("name");
					if(mArchivesDatabase.removeData(name)){
						Toast.makeText(getApplicationContext(), "Archivo "+ name + " borrado", Toast.LENGTH_LONG).show();
					}
				}
				break;
			case BLOCKED_USERS_REQUEST: // Caso para cuando se vuelve de ver los usuarios bloqueados. Hay que recargar la lista.
				if(resultCode == Activity.RESULT_OK){
					al_blocked_users.clear();
					al_blocked_users = (ArrayList<Friends>) data.getSerializableExtra("arrayBloqueados");
					// Si se ha bloqueado a un amigo hay que recargar el arrayList y el adapter.
					loadFoldersAccess();
					populateListView();
				}
				break;
			case SEE_SHARED_FOLDERS_REQUEST:
				loadSharedFolders();
				loadFoldersAccess();
				break;
			case 6:
				try {
					boolean download = data.getBooleanExtra("download",false);
					boolean returnGroups = data.getBooleanExtra("returnGroups",false);
					if (download){
						String name = data.getStringExtra("name");
						String owner = data.getStringExtra("owner");
						Boolean preview = data.getBooleanExtra(Utils.REQ_PREVIEW, false);
						userRecursos = owner;
						publish(owner,"RA",name, preview);

						Intent Intent = new Intent(this, DownloadManagerActivity.class);
						Intent.putExtra("downloadServiceIntent", this.dl_intent);
						startActivity(Intent);

					}else {// Si hay grupos nuevos, modificados o con ficheros moodificados:
						newgroups = (ArrayList<Groups>) data.getSerializableExtra("newgroups");
						if (!newgroups.isEmpty()) {
							//Para cada grupo, debemos ver los usuarios que tiene cada uno y estrablecer una conexión con ellos, y enviar la informacion del grupo
							for (int i = 0; i < newgroups.size(); i++) {
								Groups NGGroup = newgroups.get(i);
								ArrayList<Friends> friendslist = NGGroup.getListFriends();
								for (int j = 0; j < friendslist.size(); j++) {
									userRecursos = friendslist.get(j).getNombre();
									publish(friendslist.get(j).getNombre(), "NG", NGGroup.getNameGroup(),false);
								}
							}
						}
						//si hay grupos que borrar:
						ArrayList<Groups> deletegroups = (ArrayList<Groups>) data.getSerializableExtra("deletegroups");
						if (!deletegroups.isEmpty()) {
							//Para cada grupo, debemos ver los usuarios que tiene cada uno y establecer una conexión con ellos, y borrarlo
							for (int i = 0; i < deletegroups.size(); i++) {
								Groups DGGroup = deletegroups.get(i);
								ArrayList<Friends> friendslist = DGGroup.getListFriends();
								for (int j = 0; j < friendslist.size(); j++) {
									userRecursos = friendslist.get(j).getNombre();
									publish(friendslist.get(j).getNombre(), "DG", DGGroup.getNameGroup(),false);
								}
							}
						}
						if(returnGroups) {
							Intent intent = new Intent(Profile.this, listGroupsActivity.class);
							intent.putExtra("username", username);
							startActivityForResult(intent, 6);
						}
					}
				}catch(Exception e) {

				}
				break;
			default:
				if(!userRecursos.equals("")){
					cerrarConexion(userRecursos);
					userRecursos = "";
					userRecursos = "";
				}
				break;
		}
	}
	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		if (requestCode == 1) {
			if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				Profile.this.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(Profile.this, "Ahora puedes compartir archivos :)", Toast.LENGTH_SHORT).show();
					}
				});
			} else {
				Profile.this.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(Profile.this, "No se puede acceder a los archivos", Toast.LENGTH_SHORT).show();
					}
				});
			}
			return;
		}
	}
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.my_toolbar, menu);
		MenuItem myActionMenuItem = menu.findItem( R.id.action_search);
		final MenuItem addFriendMenuItem = menu.findItem( R.id.add_friend);
		final MenuItem blockMenuItem = menu.findItem( R.id.block_upload_mobile);
		SearchView searchFriend = (SearchView) myActionMenuItem.getActionView();
		searchFriend.setQueryHint(getText(R.string.search_friend));
		searchFriend.setOnSearchClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				addFriendMenuItem.setVisible(false);
				blockMenuItem.setVisible(false);
			}
		});
		searchFriend.setOnCloseListener(new SearchView.OnCloseListener() {
			@Override
			public boolean onClose() {
				addFriendMenuItem.setVisible(true);
				blockMenuItem.setVisible(true);
				return false;
			}
		});
		searchFriend.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextSubmit(String query) {

				return false;
			}
			@Override
			public boolean onQueryTextChange(String newText) {
				Toast.makeText(getApplicationContext(), newText, Toast.LENGTH_SHORT).show();
				listFriendsSearch(newText);
				return true;
			}

		});
		return true;
	}
	private void listFriendsSearch(String text){
		ArrayList<Friends> friendsSearch = new ArrayList<>();
		if (text.length()== 0){
			friendsSearch.addAll(al_friends);
		}else{
			for(Friends temp : al_friends){
				if(temp.getNombre().toLowerCase(Locale.getDefault()).contains(text.toLowerCase())) {
					friendsSearch.add(temp);
				}
			}
		}
		adapter = new FriendsAdapter(this, friendsSearch);
		friends_list.setAdapter(adapter);

	}

	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.block_upload_mobile:
				mobileDataBlocked = !mobileDataBlocked;
				if (mobileDataBlocked) {
					item.setIcon(R.drawable.data_off);
					Toast.makeText(getApplicationContext(), "Ya no se comparten archivos con datos móviles", Toast.LENGTH_LONG).show();
				}
				else {
					item.setIcon(R.drawable.data_on);
					Toast.makeText(getApplicationContext(), "Ahora se pueden compartir archivos con datos móviles", Toast.LENGTH_LONG).show();
				}
				return true;

			case R.id.see_shared_folders:
				Intent sfIntent = new Intent(this, SharedFoldersActivity.class);
				sfIntent.putExtra("friends", al_friends);
				sfIntent.putExtra("sharedFolders", sharedFolders);
				sfIntent.putExtra("foldersAccess", foldersAccess);
				startActivityForResult(sfIntent, SEE_SHARED_FOLDERS_REQUEST);
				return true;

			case R.id.see_downloads:
				Intent dmIntent = new Intent(this, DownloadManagerActivity.class);
				dmIntent.putExtra("downloadServiceIntent", this.dl_intent);
				startActivity(dmIntent);
				return true;

			case R.id.see_blocked_users:
				Intent BUintent = new Intent(this, BlockedUsersActivity.class);
				BUintent.putExtra("amigos", al_friends);
				startActivityForResult(BUintent, BLOCKED_USERS_REQUEST);
				return true;

			case R.id.see_shared_archives:
				final ArrayList<String> al = getArchivesList();
				Intent intent = new Intent(Profile.this, Recursos.class);
				intent.putExtra("lista", al);
				intent.putExtra("listener", false);
				startActivityForResult(intent, 3);
				return true;

			case R.id.add_friend:
				mdialog = new Dialog(Profile.this);
				mdialog.setContentView(R.layout.dialog_newfriend);
				mdialog.show();
				name = mdialog.findViewById(R.id.name);
				Button bf = mdialog.findViewById(R.id.button_friend);

				bf.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						final String fr = name.getText().toString();
						/* Si el nuevo amigo estaba bloqueado se elimina el bloqueo antes de enviar
						 * la petición de amistad.
						 */
						if (listContains(fr, al_blocked_users)){
							final Dialog removeBlockedDialog = new Dialog(Profile.this);
							removeBlockedDialog.setContentView(R.layout.dialog_remove_blocked_friend);
							removeBlockedDialog.show();

							TextView title = removeBlockedDialog.findViewById(R.id.previous_blocked_friend_title);
							String tmp=fr + " está bloqueado y se desbloqueará si continúas.\n¿Continuar?";
							title.setText(tmp);

							Button yes = removeBlockedDialog.findViewById(R.id.unlock_yes);
							yes.setOnClickListener(new View.OnClickListener() {
								@Override
								public void onClick(View view) {
									mDatabaseHelper.removeData(fr, DatabaseHelper.BLOCKED_TABLE_NAME);
									loadBlockedUsersList();
									publish(fr, "FR","",false);
									Toast.makeText(getApplicationContext(), "Petición de amistad enviada", Toast.LENGTH_SHORT).show();
									removeBlockedDialog.dismiss();
								}
							});
							Button no = removeBlockedDialog.findViewById(R.id.unlock_no);
							no.setOnClickListener(new View.OnClickListener() {
								@Override
								public void onClick(View view) {
									removeBlockedDialog.dismiss();
								}
							});
						}
						else if(!listContains(fr, al_friends)) {
							publish(fr, "FR","",false);
							Toast.makeText(getApplicationContext(), "Petición de amistad enviada", Toast.LENGTH_SHORT).show();
						}else{
							Toast.makeText(getApplicationContext(), "Ya eres amigo de " + fr, Toast.LENGTH_SHORT).show();
						}
						mdialog.dismiss();
					}
				});
				return true;
			default:
			// If we got here, the user's action was not recognized.
			// Invoke the superclass to handle it.
			return super.onOptionsItemSelected(item);
		}
	}
	private boolean listContains(String nombre, ArrayList<Friends> al){
		for(Friends f : al){
			if(f.getNombre().equals(nombre)){
				return true;
			}
		}
		return false;
	}
	private void addData(String newEntry){ //llamar cuando aceptemos la peticion de amistad y cuando nos la acepten
		boolean insertData = mDatabaseHelper.addData(newEntry, DatabaseHelper.FRIENDS_TABLE_NAME);
		if(insertData){
			populateListView();
		}
	}
	private void populateListView(){
		Cursor data = mDatabaseHelper.getData(DatabaseHelper.FRIENDS_TABLE_NAME);
		al_friends = new ArrayList<>();
		while(data.moveToNext()){
			al_friends.add(new Friends(data.getString(1), R.drawable.astronaura));
		}
		adapter = new FriendsAdapter(this, al_friends);
		friends_list.setAdapter(adapter);
		data.close();
	}
	private ArrayList<String> getArchivesList(){
		ArrayList<String> al = new ArrayList<>();
		Cursor data = mArchivesDatabase.getData();
		while(data.moveToNext()){
			al.add(data.getString(1));
		}
		data.close();
		return al;
	}

	private void connectPeer(String connectTo, boolean call)throws Exception{
		if(pnRTCClient == null) {
			PeerConnectionFactory.initializeAndroidGlobals(
					getApplicationContext(),  // Context
					true,  // Audio Enabled
					true,  // Video Enabled
					true,  // Hardware Acceleration Enabled
					VideoRendererGui.getEGLContext()); // Render EGL Context

			PeerConnectionFactory pcFactory = new PeerConnectionFactory();
			pnRTCClient = new PnRTCClient(userPubNub.pubnub(Constants.PUB_KEY), userPubNub.pubnub(Constants.SUB_KEY), this.username);

			MediaStream mediaStream = pcFactory.createLocalMediaStream(LOCAL_MEDIA_STREAM_ID);

			pnRTCClient.attachRTCListener(new myRTCListener());
			pnRTCClient.attachLocalMediaStream(mediaStream);

			pnRTCClient.listenOn(this.username);
		}

		if(call){
			pnRTCClient.connect(connectTo);
		}
	}

	private void notificate(final String notification){
		Profile.this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(getApplicationContext(), notification, Toast.LENGTH_SHORT).show();
			}
		});
	}
	private void cerrarConexion(String userTo){
		pnRTCClient.closeConnection(userTo);
	}

	private void RA(String name, String sendTo, boolean isPreview, final boolean group){ //Request Archive
		try{
			JSONObject msg = new JSONObject();
			final String finalName = name;
			msg.put("type", "RA");
			msg.put(SENDTO, this.username);
			msg.put(Utils.NAME, name);
			msg.put(Utils.REQ_PREVIEW, isPreview);
			msg.put("group", group);
			//CIFRADO Paso1. Envio de la clave publica
			msg.put("publicKey",rsaUser.getPublicKeyString());
			Log.i("paso1-send:publickey",rsaUser.getPublicKeyString());
			// Útil para la descarga desde una carpeta compartida:
			if (selectedFolder != null){
				msg.put("selectedFolder", selectedFolder);
				selectedFolder = null;
			}
			/*
			 * Si hay hilos de descarga disponibles se lanza.
			 * si no, se añade a la cola y ya conectaré con el emisor.
			 */
			if (downloadService.hasFreeThreads()) {
				Profile.this.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if(group)
							Toast.makeText(getApplicationContext(), "Descargando " + finalName.substring(finalName.lastIndexOf('/')+1), Toast.LENGTH_LONG).show();
						else
							Toast.makeText(getApplicationContext(), "Descargando " + finalName, Toast.LENGTH_LONG).show();
					}
				});
				prepareDownloaderClient(sendTo);
				downloaderClient.transmit(sendTo, msg);
				//pnRTCClient.transmit(sendTo, msg);
			}
			else{
				downloadService.queueMsg(sendTo, msg);
				Profile.this.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(getApplicationContext(), finalName + " puesto en cola", Toast.LENGTH_LONG).show();
					}
				});
			}
		}catch(Exception e){

		}
	}
	/**
	 * Inicializa el cliente que va a recibir los datos de ficheros.
	 * @param user Usuario con el que se establece la conexión.
	 */
	private void prepareDownloaderClient(String user) throws Exception{
		PeerConnectionFactory pcFactory = new PeerConnectionFactory();
		String uuid = "downloader_" + username;
		downloaderClient = new PnRTCClient(userPubNub.pubnub(Constants.PUB_KEY), userPubNub.pubnub(Constants.SUB_KEY), uuid);
		MediaStream mediaStream = pcFactory.createLocalMediaStream(LOCAL_MEDIA_STREAM_ID_DOWNLOADER);
		downloaderClient.attachLocalMediaStream(mediaStream);
		downloaderClient.attachRTCListener(new myRTCListener());
		downloaderClient.listenOn(uuid);
		downloaderClient.connect(user);
	}

	private void handleSA(JSONObject jsonMsg){
		//CIFRADO Paso4. Se recibe el mensaje la secretKey encriptada y con la info cifrada y se la pasa a dowloadService
		//vamos a añadir en el mensaje la secretKey descifrada para que pueda descodificar el mensaje
		boolean download=false;
		try {
			Cryptography rsaTemp =new Cryptography();
			String  pubkeyString = jsonMsg.getString("publicKey");
			Log.i("paso4-send:pubkey2",pubkeyString);
			rsaTemp.setPublicKeyString(pubkeyString);
			String secretKeyCipher = jsonMsg.getString("secretKey");
			Log.i("paso4-send:secretkeyCif",secretKeyCipher);
			String secretKeySign = jsonMsg.getString("signature");
			Log.i("paso4-send:secretkeySig",secretKeySign);
			Boolean verify = rsaTemp.verifyRSA(secretKeySign,secretKeyCipher);
			Log.i("paso4-send:verify", String.valueOf(verify));
			if (verify){
				String secretKey = rsaUser.decipherRSAToString(secretKeyCipher);
				jsonMsg.put("secretKey",secretKey);
				Log.i("paso4-send:secretkey",secretKey);
				download=true;
			}else{
				Toast.makeText(this, "Error, la informacion recibida no es segura.", Toast.LENGTH_LONG).show();
			}
		} catch (Exception e) {

		}
		if (download){
			this.downloadService.handleMsg(jsonMsg);
		}
	}

	private void handleRA(JSONObject jsonMsg){
		try{
			// Primero se comprueba si se está conectado a Internet con datos móviles:
			final ConnectivityManager connMgr = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
			boolean isUsingMobile = Objects.requireNonNull(connMgr).getNetworkInfo(ConnectivityManager.TYPE_MOBILE).isConnectedOrConnecting();
			// En caso de que sí y se haya seleccionado que no se usen en la barra superior, se descarta la petición.
			if (!(isUsingMobile && mobileDataBlocked)) {
				boolean cancel_dl;
				try {
					cancel_dl = jsonMsg.getBoolean(Utils.CANCEL_DL);
				} catch (JSONException e) {
					cancel_dl = false;
				}
				if (!cancel_dl) {
					final String user = jsonMsg.getString(SENDTO);
					// Si el usuario no está bloqueado se procede, en otro caso se desecha la petición silenciosamente.
					if (!listContains(user, al_blocked_users)) {
						String archive = jsonMsg.getString(Utils.NAME);
						String sendTo = jsonMsg.getString(SENDTO);
						String folder;
						try {
							folder = jsonMsg.getString("selectedFolder");
						} catch (JSONException e) {
							folder = null;
						}

						String path;
						boolean group = jsonMsg.getBoolean("group");
						if (group){
							path = archive;
							archive= archive.substring(archive.lastIndexOf('/')+1);

						}else {
							if (folder != null) {
								path = folder + '/' + archive;
							} else {
								Cursor c = this.mArchivesDatabase.getData(archive);
								c.moveToNext();
								path = c.getString(1);
								c.close();
							}
						}

						JSONObject msg = new JSONObject();
						msg.put(Utils.FRIEND_NAME, this.username);
						msg.put("type", "SA");
						msg.put(Utils.NAME, archive);

						File file;
						final FileInputStream fis;
						long previewSize = 0;
						final boolean isPreview;
						isPreview = jsonMsg.getBoolean(Utils.REQ_PREVIEW);
						if (isPreview) {
							// La cantidad de datos que se van a enviar para una previsualización dependerá del tipo de archivo:
							String extension = archive.substring(archive.lastIndexOf('.') + 1).toLowerCase();
							file = prepareCutDocument(path, extension);
							previewSize = setPreviewSize(extension);
							if (previewSize > 0)
								file = new File(path);
							else
								previewSize = file.length();
							msg.put(Utils.PREVIEW_SENT, true);
						} else {
							file = new File(path);
							msg.put(Utils.PREVIEW_SENT, false);
						}
						fis = new FileInputStream(file);

						int fileLength = (int) file.length();

						msg.put(Utils.FILE_LENGTH, fileLength);
						msg.put(Utils.NEW_DL, true);

						//CIFRADO Paso2. Obtengo el string de la clave publica
						// Genero SecretKey para cifrar en activeFileSender
						// encripto la secreKey con la clave publica y guardo en mensaje
						String pubkeyString = jsonMsg.getString("publicKey");
						Log.i("paso2-reci:publickey", pubkeyString);
						Cryptography rsaTemp = new Cryptography();
						rsaTemp.setPublicKeyString(pubkeyString);
						rsaTemp.generateKey();
						String secretKey = rsaTemp.getSecretKeyString();
						Log.i("paso2-reci:secretkey", secretKey);
						String secretKeyCipher = rsaTemp.cipherRSA(secretKey);
						Log.i("paso2-reci:secretkeyCif", secretKeyCipher);
						msg.put("secretKey", secretKeyCipher);
						String secretKeySign = rsaUser.signRSA(secretKeyCipher);
						Log.i("paso2-reci:secretkeyFir", secretKeySign);
						msg.put("signature", secretKeySign);
						msg.put("publicKey", rsaUser.getPublicKeyString());
						Log.i("paso2-reci:signature", secretKeySign);
						Log.i("paso2-reci:publickey2", rsaUser.getPublicKeyString());


						// Si no se está enviando ningún archivo y no hay ningún hilo en cola se lanza el hilo de subida.
						if (!sendingFile && sendersManager.isQueueEmpty()) {
							sendingFile = true;
							activeFileSender = new FileSender();
							activeFileSender.setName("fileSender");
							activeFileSender.setVariables(previewSize, msg, sendTo, file, fis, isPreview, secretKey);
							activeFileSender.start();
						}
						// Si hay un hilo enviando un fichero y la cola no está llena se pone en cola.
						else if (sendingFile && !sendersManager.queueFull()) {
							FileSender fs = new FileSender();
							fs.setName("fileSenderQueued");
							fs.setVariables(previewSize, msg, sendTo, file, fis, isPreview, secretKey);
							sendersManager.addSender(archive, fs);
						}
					}
				} else {
					try {
						String uploadFileName = jsonMsg.getString(Utils.NAME);
						// Si la subida cancelada es la activa se para.
						if (uploadFileName.equals(activeFileSender.getFileName()))
							activeFileSender.stopUpload();
						// Si está en cola se elimina.
						else
							sendersManager.removeSender(uploadFileName);
					} catch (JSONException e) {

					}
				}
			}
		}catch(Exception e){

		}
	}
	/**
	 * Prepara el fichero que se va a enviar para ser previsualizado en el destino.
	 * @param path Ruta del fichero.
	 * @param extension Extensión o tipo.
	 * @return Fichero de tamaño reducido.
	 */
	private File prepareCutDocument(String path, String extension){
		File f;
		final String preview = "_preview";
		switch (extension){
			// Si es un pdf se crea uno nuevo con 3 páginas.
			case "pdf":
				f = new File(path);
				try (PDDocument pdf = PDDocument.load(f);PDDocument pd = new PDDocument();){
					Splitter splitter = new Splitter();
					List<PDDocument> pages = splitter.split(pdf);
					Iterator<PDDocument> it = pages.listIterator();

					// Se meten 3 páginas.
					PDDocument aux;
					byte i = 0;
					while (it.hasNext() && i<3){
						aux = it.next();
						pd.addPage(aux.getPage(0));
						aux.close();
						++i;
					}

					f = new File(path+preview);
					pd.save(f);
					pd.close();
				}catch (IOException e){
					f = new File(path);
				}
				break;
			// Si es una imagen de uno de los tipos soportados se crea otra de calidad reducida.
			case "jpg":
			case "jpeg":
			case "png":
				f = createThumbnail(path, extension);
				break;
			//TODO
			/*case "mp4": break;
			case "avi": break;*/
			// Si no es ninguno de estos tipos de archivo entonces se pone la ruta normal.
			default: f = new File(path); break;
		}
		return f;
	}
	/**
	 * Crea una imagen de calidad reducida para la previsualización haciendo uso de las utilidades
	 * de android para crear miniaturas.
	 * @param path Ruta de la imagen.
	 * @param ext Extensión de la imagen.
	 * @return Archivo de imagen de menor calidad.
	 */
	private File createThumbnail(String path, String ext){
		int width, height;
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(path, options);
		width = options.outWidth;
		height = options.outHeight;
		Bitmap bmp = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(path), width, height);
		File f = new File(path+"_preview");
		try(FileOutputStream fos = new FileOutputStream(f);){
			if (ext.equalsIgnoreCase("png"))
				bmp.compress(Bitmap.CompressFormat.PNG, 40, fos);
			else if (ext.equalsIgnoreCase("jpg") || ext.equalsIgnoreCase("jpeg"))
				bmp.compress(Bitmap.CompressFormat.JPEG, 40, fos);
			fos.close();
		} catch (Exception e){
			//noinspection ResultOfMethodCallIgnored
			f.delete();
		}
		return f;
	}
	/**
	 * Cuando el usuario remoto quiere previsualizar un archivo hay que enviarle cierta cantidad de
	 * datos, la cual depende del tipo de archivo. Esté método determina la cantidad de datos que se
	 * van a enviar en función del tipo del archivo si procede limitarlo de esta manera.
	 * Los tamaños que no son establecidos en este método son determinados de otra forma ya que no se
	 * pueden concretar por su naturaleza (archivos de vídeo, imagen, pdf...). Por ejemplo, no es posible
	 * saber si 10KB es información suficientemente relevante para un pdf sin abrirlo.
	 * @param ext Extensión del archivo.
	 * @return Tamaño máximo de datos para el envío.
	 */
	private int setPreviewSize(String ext){
		int maxSize;
		switch (ext){
			case "txt":
			case "html":
			case "css":
				maxSize = 16*1024; break;
			case "mp3": maxSize = 1024*1024; break;
			default: maxSize = 0;
		}
		return maxSize;
	}

	private void FR(String sendTo){ //Friend Request
		try{
			JSONObject msg = new JSONObject();
			msg.put("type", "FR");
			msg.put(SENDTO, this.username);

			pnRTCClient.transmit(sendTo, msg);
		}catch(Exception e){

		}
	}
	private void handleFR(JSONObject jsonMsg){
		try{
			final String userFR = jsonMsg.getString(SENDTO);
			// Si el usuario está bloqueado se desecha la petición silenciosamente.
			if (!listContains(userFR, al_blocked_users)){
				ArrayList<String> friendsStrings = Utils.getFriendsArrayListAsStrings(al_friends);
				// Si el usuario no estaba agregado como amigo se añade:
				if (!friendsStrings.contains(userFR)) {
					mdialog = new Dialog(Profile.this);
					mdialog.setContentView(R.layout.dialog_acceptfriend);
					mdialog.show();
					TextView f_name = mdialog.findViewById(R.id.accept_friend_tv);
					String tmp="¿Quieres aceptar a " + userFR + " como amigo?";
					f_name.setText(tmp);

					Button yes = mdialog.findViewById(R.id.accept_friend_yes);
					Button no = mdialog.findViewById(R.id.accept_friend_no);
					no.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							mdialog.dismiss();
							cerrarConexion(userFR);
						}
					});
					yes.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							Profile.this.runOnUiThread(new Runnable() {
								@Override
								public void run() {
									addData(userFR);
								}
							});
							FA(userFR);
							mdialog.dismiss();
						}
					});
				}
				// Si ya lo estaba simplemente se le notifica.
				else{
					FA(userFR);
					mdialog.dismiss();
				}
			}
		}catch(Exception e){

		}
	}

	private void FA(String sendTo){
		try{
			JSONObject msg = new JSONObject();
			msg.put("type", "FA");
			msg.put("addme", this.username);

			pnRTCClient.transmit(sendTo, msg);
		}catch(Exception e){

		}
	}
	private void handleFA(JSONObject jsonMsg){
		try{
			String addme = jsonMsg.getString("addme");
			addData(addme);
			cerrarConexion(addme);
		}catch(Exception e){

		}
	}

	private void VAR(String sendTo){ //envia peticion para ver archivos
		try{
			JSONObject msg = new JSONObject();
			msg.put("type", "VAR"); //tipo de mensaje
			msg.put(SENDTO, this.username); //usuario para devolver mensaje con datos
			pnRTCClient.transmit(sendTo, msg);
		}catch(Exception e){

		}
	}
	/**
	 * Método que envía la petición para ver las carpetas compartidas de un amigo.
	 * @param sendTo
	 */
	private void VSF(String sendTo){
		try{
			JSONObject msg = new JSONObject();
			msg.put("type", "VSF");
			msg.put(SENDTO, this.username);
			pnRTCClient.transmit(sendTo, msg);
		}catch(Exception e){

		}
	}

	private void handleVAL(JSONObject jsonMsg){
		try{
			boolean blocked = jsonMsg.getBoolean("blocked");
			if (!blocked) {
				ArrayList<String> al = new ArrayList();
				int size = jsonMsg.getInt("size");
				for (int i = 0; i < size; i++) {
					al.add(jsonMsg.getString("item" + i));
				}
				Intent intent = new Intent(Profile.this, Recursos.class);
				intent.putExtra("lista", al);
				intent.putExtra("listener", true);
				intent.putExtra(SENDTO, jsonMsg.getString(SENDTO));
				startActivityForResult(intent, 2); //para volver a esta activity, llamar finish() desde la otra.
			}
			else
				Toast.makeText(this, "No puedes ver los archivos", Toast.LENGTH_LONG).show();

		}catch(Exception e){

		}
	}

	private void VAL(JSONObject jsonMsg){
		try{
			final String userFR = jsonMsg.getString(SENDTO);
			JSONObject msg = new JSONObject();
			msg.put("type", "VAL");
			msg.put(SENDTO, this.username);
			// Si el usuario no está bloqueado...
			if (!listContains(userFR, al_blocked_users)) {
				ArrayList<String> al = getArchivesList();
				msg.put("blocked", false);
				msg.put("size", al.size());
				int i = 0;
				for (String item : al) {
					msg.put("item" + i, item);
					i++;
				}
			}
			else{
				msg.put("blocked", true);
			}
			pnRTCClient.transmit(jsonMsg.getString(SENDTO), msg);
		}catch(Exception e){

		}
	}
	/**
	 * Maneja la petición de ver carpetas compartidas por parte de un amigo.
	 * @param jsonMsg
	 */
	private void handleVSF(JSONObject jsonMsg){
		try{
			final String userFR = jsonMsg.getString(SENDTO);
			JSONObject msg = new JSONObject();
			// Si el usuario no está bloqueado...
			if (!listContains(userFR, al_blocked_users)) {
				// Si el amigo tiene acceso a una o más carpetas, se le envían:
				HashMap<String,ArrayList<String>> sf = getFriendAllowedFolders(userFR);
				if (!sf.isEmpty()) {
					msg = new JSONObject(sf);
					msg.put(Utils.FOLDERSHARING_SPECIAL_CHARS + "type", "SF");
					msg.put(Utils.FOLDERSHARING_SPECIAL_CHARS + "SFallowed", true);
					msg.put(Utils.FOLDERSHARING_SPECIAL_CHARS + "blocked", false);
					msg.put(Utils.FOLDERSHARING_SPECIAL_CHARS + "foldersCount", sf.size());
					msg.put(Utils.FOLDERSHARING_SPECIAL_CHARS + SENDTO, this.username);
				}
				//Si no tiene acceso a ninguna carpeta compartida también se le hace saber:
				else {
					msg.put(Utils.FOLDERSHARING_SPECIAL_CHARS + "type", "SF");
					msg.put(Utils.FOLDERSHARING_SPECIAL_CHARS + "SFallowed", false);
					msg.put(Utils.FOLDERSHARING_SPECIAL_CHARS + "blocked", false);
				}
			}
			else{
				msg.put(Utils.FOLDERSHARING_SPECIAL_CHARS + "blocked", true);
			}
			pnRTCClient.transmit(jsonMsg.getString(SENDTO), msg);
		}catch(Exception e){

		}
	}
	/**
	 * Maneja la respuesta a la petición de ver carpetas compartidas.
	 */
	private void handleSF(JSONObject json){
		try {
			// Primero se comprueba si estoy bloqueado por si acaso.
			boolean blocked = json.getBoolean(Utils.FOLDERSHARING_SPECIAL_CHARS + "blocked");
			if (!blocked) {
				// Después se comprueba si tengo permitido el acceso a alguna carpeta.
				boolean allowed = json.getBoolean(Utils.FOLDERSHARING_SPECIAL_CHARS + "SFallowed");
				if (allowed) {
					int size = json.getInt(Utils.FOLDERSHARING_SPECIAL_CHARS + "foldersCount");
					final String sendTo = json.getString(Utils.FOLDERSHARING_SPECIAL_CHARS + SENDTO);
					final HashMap<String, ArrayList<String>> map = new HashMap<>(size);

					Iterator<String> keysIt = json.keys();
					while (keysIt.hasNext()) {
						String key = keysIt.next();
						// Si no empieza con los caracteres especiales entonces es la info de una carpeta.
						if (!key.startsWith(Utils.FOLDERSHARING_SPECIAL_CHARS)) {
							JSONArray jsonArray = (JSONArray) json.get(key);
							ArrayList<String> filesList = new ArrayList<>(jsonArray.length());
							for (int i=0; i<jsonArray.length(); i++)
								filesList.add(jsonArray.getString(i));
							map.put(key, filesList);
						}
					}
					// Reutilizo el diálogo para ver el contenido de una carpeta compartida propia.
					Profile.this.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							final Dialog dialog = new Dialog(Profile.this);
							dialog.setContentView(R.layout.dialog_see_files);
							TextView title = dialog.findViewById(R.id.folder_name);
							String tmp="Carpetas disponibles";
							title.setText(tmp);
							final ArrayList<String> foldersArray = new ArrayList<>(map.keySet());
							AEArrayAdapter adapter = new AEArrayAdapter(Profile.this, android.R.layout.simple_list_item_1, foldersArray);
							ListView folders_list = dialog.findViewById(R.id.files_list);
							folders_list.setAdapter(adapter);
							folders_list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
								@Override
								public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
									selectedFolder = foldersArray.get(i);
									Intent intent = new Intent(Profile.this, Recursos.class);
									intent.putExtra("isFS", true);
									intent.putStringArrayListExtra("lista", map.get(selectedFolder));
									intent.putExtra("listener", true);
									intent.putExtra(SENDTO, sendTo);
									startActivityForResult(intent, 2);
								}
							});
							dialog.show();
						}
					});
				}
				// Si no tengo permitido el acceso se muestra un mensaje:
				else
					Profile.this.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							Toast.makeText(Profile.this, "No puedes ver las carpetas", Toast.LENGTH_SHORT).show();
						}
					});
			}
		}
		catch (JSONException e){

		}
	}
	/**
	 * Devuelve las carpetas a las que tiene acceso el usuario que hace la petición.
	 */
	private HashMap<String,ArrayList<String>> getFriendAllowedFolders(String user){
		HashMap<String,ArrayList<String>> result = new HashMap<>();
		Iterator it = foldersAccess.entrySet().iterator();
		while (it.hasNext()){
			Map.Entry item = (Map.Entry) it.next();
			ArrayList users = (ArrayList<String>) item.getValue();
			if (users.contains(user)){
				String folder = (String) item.getKey();
				result.put(folder, sharedFolders.get(folder));
			}
		}
		return result;
	}
	/**
	 * Hilo para el envío de 1 archivo.
	 */
	public class FileSender extends Thread {
		private long previewLength;
		private JSONObject msg;
		private String sendTo2;
		private File file2;
		private FileInputStream fis;
		private boolean isPreview;
		private String secretKey;
		@Override
		public void run() {
			boolean lastPiece = false;
			boolean firstPiece = true;
			// Voy a enviar 16 KB (16384) de datos en cada mensaje, codificado aumentará.
			byte[] bFile = new byte[16384];
			int bytesRead;
			int totalBytesRead = 0;
			String s;
			activeFileSender = this;
			pnRTCClient.closeConnection(sendTo2);
			try {
				prepareSenderClient(sendTo2);
			} catch (Exception e) {

			}
			try {

				//CIFRADO Paso3. Genero clase donde guardo la secretKey y con la que cifro los mensajes
				int count=0;
				Cryptography rsaTemp = new Cryptography();
				rsaTemp.setSecretKeyString(secretKey);
				Log.i("paso3-reci:secretkey",rsaTemp.getSecretKeyString());

				while (!lastPiece) {
					bytesRead = fis.read(bFile);
					totalBytesRead += bytesRead;
					if (isPreview)
						lastPiece = (totalBytesRead >= previewLength) || (bytesRead < bFile.length);
					else
						lastPiece = (bytesRead < bFile.length);

					msg.put(Utils.LAST_PIECE, lastPiece);
					/* Si es el último fragmento sólo hay que enviar los datos leídos. De otro modo
					 * se enviarían datos que no han sido sobreescritos en el byte[] usado en el resto
					 * de la transmisión.
					 */
					if (lastPiece){
						// Si bytesRead == -1 no hay que enviar nada más.
						byte[] finalbFile;
						if (bytesRead != -1) {
							finalbFile = Arrays.copyOf(bFile, bytesRead);
						}
						else {
							finalbFile = new byte[]{0};
						}
						//CIFRADO Paso3. Uso la secretKey para cifrar el string con la parte del fichero enviado
						Log.i("paso3-reci:nocif", Cryptography.bytesToString(finalbFile));
						Log.i("paso3-reci:nocifsize", String.valueOf(Cryptography.bytesToString(finalbFile).length()));
						s = rsaTemp.cipherSimetric(finalbFile);
						Log.i("paso3-reci:cif",s);
						Log.i("paso3-reci:cifsize", String.valueOf(s.length()));
						//como estaba antes
						// s = Base64.encodeToString(finalbFile, Base64.URL_SAFE);
					}
					else {
						//CIFRADO Paso3. Uso la secretKey para cifrar el string con la parte del fichero enviado
						Log.i("paso3-reci:nocifrado", Cryptography.bytesToString(bFile));
						Log.i("paso3-reci:nocifsize", String.valueOf(Cryptography.bytesToString(bFile).length()));

						s = rsaTemp.cipherSimetric(bFile);
						Log.i("paso3-reci:cifrado",s);
						Log.i("paso3-reci:cifradosize", String.valueOf(s.length()));

						//como estaba antes
						// s = Base64.encodeToString(bFile, Base64.URL_SAFE);

					}
					msg.put(Utils.DATA, s);
					msg.put("count",count);
					Log.i("counted-send", String.valueOf(count));
					senderClient.transmit(sendTo2, msg);
					msg.remove(Utils.DATA);
					msg.remove(Utils.LAST_PIECE);

					if (firstPiece) {
						msg.remove(Utils.FILE_LENGTH);
						msg.remove(Utils.NEW_DL);
						msg.put(Utils.NEW_DL, false);
						firstPiece = false;
					}
					count++;
				}
				// Si ha sido necesario crear un archivo nuevo hay que borrarlo.
				if (file2.getName().contains("_preview")) {
					//noinspection ResultOfMethodCallIgnored
					file2.delete();
				}

				fis.close();
				senderClient.closeConnection(sendTo2);
				sendingFile = false;

				if (sendersManager.hasSender(file2.getName()))
					sendersManager.removeSender(file2.getName());

				// Se avisa al manager de que se ha terminado la subida y puede lanzar la siguiente en la cola, si existe:
				sendersManager.notifyFinishedUpload();
			} catch (Exception e){

			}
		}
		/**
		 * Inicializa el cliente que va a enviar los datos de ficheros.
		 * @param user Usuario con el que se establece la conexión.
		 */
		private void prepareSenderClient(String user) throws Exception{
			PeerConnectionFactory pcFactory = new PeerConnectionFactory();
			String uuid = "sender_" + username;
			//String uuid = "sender_" + (++senderCount) + '_' + username;
			senderClient = new PnRTCClient(userPubNub.pubnub(Constants.PUB_KEY), userPubNub.pubnub(Constants.SUB_KEY), uuid);
			MediaStream mediaStream = pcFactory.createLocalMediaStream(LOCAL_MEDIA_STREAM_ID_SENDER);
			//MediaStream mediaStream = pcFactory.createLocalMediaStream(LOCAL_MEDIA_STREAM_ID_2+senderCount);
			senderClient.attachLocalMediaStream(mediaStream);
			senderClient.attachRTCListener(new myRTCListener());
			senderClient.listenOn(uuid);
			senderClient.connect(user);
		}
		/**
		 * Método para inicializar las variables del hilo, es obligatorio llamarlo antes de
		 * arrancar el hilo.
		 * @param p Tamaño del fichero en caso de una previsualización, 0 si no lo es.
		 * @param j Mensaje JSON.
		 * @param s Nombre del destinatario.
		 * @param f Fichero.
		 * @param fiss Canal de transmisión de los datos del fichero para su lectura.
		 * @param prev Determina si es un fichero de previsualización o no.
		 */
		void setVariables(long p, JSONObject j, String s, File f, FileInputStream fiss, boolean prev, String pk){
			previewLength = p;
			msg = j;
			sendTo2 = s;
			file2 = f;
			fis = fiss;
			isPreview = prev;
			secretKey = pk;
		}
		/**
		 * Devuelve el nombre del fichero que se está descargando.
		 * @return nombre del fichero.
		 */
		String getFileName(){
			return file2.getName();
		}
		/**
		 * Detiene el envío actual e interrumpe el hilo.
		 */
		void stopUpload(){
			try{
				fis.close();
				senderClient.closeConnection(sendTo2);
				this.interrupt();
			} catch (IOException e){

			}
		}
	}
	private class myRTCListener extends PnRTCListener{
		@Override
		public void onPeerConnectionClosed(PnPeer peer) {
			super.onPeerConnectionClosed(peer);
		}
		@Override
		public void onLocalStream(MediaStream localStream) {
			super.onLocalStream(localStream);
		}
		public void onConnected(String userId){
			Log.d("Md-a", "conectado a: " + userId);
		}
		@Override
		public void onMessage(PnPeer peer, Object message) {
			if (!(message instanceof JSONObject)) return; //Ignore if not JSONObject
			final JSONObject jsonMsg = (JSONObject) message;
			try {
				final String type = jsonMsg.getString("type");
				if(type.equals("VAR")){
					VAL(jsonMsg);
				}else if(type.equals("VAL")){ //se debe manejar en la hebra principal ya que inicia una nueva actividad
					Profile.this.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							handleVAL(jsonMsg);
						}
					});
				}else if(type.equals("FR")){
					Profile.this.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							handleFR(jsonMsg);
						}
					});
				}else if(type.equals("FA")){
					Profile.this.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							handleFA(jsonMsg);
						}
					});
				}else if(type.equals("RA")){
					handleRA(jsonMsg);
				}else if(type.equals("SA")){
					handleSA(jsonMsg);
				}else if(type.equals("VSF")){
					handleVSF(jsonMsg);
				}else if(type.equals("NG")){
					handleNG(jsonMsg);
				}else if(type.equals("DG")){
					handleDG(jsonMsg);
				}
			} catch (JSONException e){
				try{
					String type = jsonMsg.getString(Utils.FOLDERSHARING_SPECIAL_CHARS + "type");
					if (type.equals("SF")){
						handleSF(jsonMsg);
					}
				}
				catch (JSONException e1) {}
			}
		}
	}
	/**
	 * Carga de los usuarios bloqueados almacenados en la BD.
	 */
	private void loadBlockedUsersList() {
		Cursor c = mDatabaseHelper.getData(DatabaseHelper.BLOCKED_TABLE_NAME);
		if (al_blocked_users != null)
			al_blocked_users.clear();
		else
			al_blocked_users = new ArrayList<>();
		while (c.moveToNext()){
			Friends f = new Friends(c.getString(1), R.drawable.astronaura);
			al_blocked_users.add(f);
		}
		c.close();
	}
	/**
	 * Carga de las carpetas compartidas.
	 */
	private void loadSharedFolders(){
		Cursor c = mDatabaseHelper.getData(DatabaseHelper.SHARED_FOLDERS_TABLE);
		if (sharedFolders != null )
			sharedFolders.clear();
		else
			sharedFolders = new HashMap<>();
		while (c.moveToNext()){
			String folder = c.getString(0);
			String files = c.getString(1);
			ArrayList<String> al_files = new ArrayList<>(Arrays.asList(files.split(",")));
			sharedFolders.put(folder, al_files);
		}
		c.close();
	}
	/**
	 * Carga de la lista de acceso de los amigos a las carpetas compartidas.
	 */
	private void loadFoldersAccess(){
		Cursor c = mDatabaseHelper.getData(DatabaseHelper.FOLDER_ACCESS_TABLE);
		if (foldersAccess != null)
			foldersAccess.clear();
		else
			foldersAccess = new HashMap<>();
		String lastFolder = null;
		ArrayList<String> al_friends = new ArrayList<>();

		while (c.moveToNext()){
			String folder = c.getString(0);
			int friendID = c.getInt(1);
			//Si la carpeta es distinta a la anterior o es la primera se crea una nueva entrada:
			if (!folder.equalsIgnoreCase(lastFolder)) {
				al_friends = new ArrayList<>(4);
				foldersAccess.put(folder, al_friends);
			}
			try {
				String friend = mDatabaseHelper.getUserName(friendID);
				al_friends.add(friend);
				lastFolder = folder;
			}
			catch (CursorIndexOutOfBoundsException e){}
		}
		c.close();
	}
	private void loadGroupList() {
		Cursor c = mDatabaseHelper.getData(DatabaseHelper.GROUPS_TABLE_NAME);
		if (listgroups != null){listgroups.clear();}
		else {listgroups = new ArrayList<>();}
		while (c.moveToNext()) {
			ArrayList<Friends> friends = stringtoArrayListFriend(c.getString(1));
			ArrayList files = stringtoArrayList(c.getString(2));
			ArrayList<Friends> owners = stringtoArrayListFriend(c.getString(3));
			Groups g = new Groups(c.getString(0), R.drawable.cohete, friends, files, owners, c.getString(4));
			listgroups.add(g);
		}
		c.close();
	}
	//pasar un String con los amigos a ArrayList de Amigos
	private ArrayList<Friends> stringtoArrayListFriend(String friends){
		if (friends == null){return new ArrayList<>();}
		ArrayList<Friends> resultado= new ArrayList<>();
		String[] friendsSeparate = friends.split(",");
		for (String s : friendsSeparate) {
			resultado.add(new Friends(s, R.drawable.astronaura));
		}
		return resultado;
	}
	//iniciar usuarios de cifrar
	private void initUser(){
		try {
			rsaUser = new Cryptography();
			rsaUser.genKeyPair();
			userPubNub = new Cryptography();
		} catch (NoSuchAlgorithmException e) {

		}
	}
	//pasar un String con los archivos a ArrayList
	private ArrayList stringtoArrayList(String files){
		if (files == null){
			return new ArrayList<>();
		}
		ArrayList resultado= new ArrayList();
		String[] filesSeparate = files.split(",");
		for (String s : filesSeparate) {
			resultado.add(s);
		}
		return resultado;
	}
	private void NG(String namefriend, String nameGroup){
		try{
			Groups g = new Groups();
			for (int i=0; i<newgroups.size(); i++){
				if (newgroups.get(i).getNameGroup().equals(nameGroup)){
					g=newgroups.get(i);
				}
			}
			JSONObject msg = new JSONObject();
			msg.put("type", "NG");
			msg.put(NAMEGROUP, nameGroup);
			msg.put("imgGroup", g.getImgGroup());
			msg.put("listFriends",arrayListFriendsToString(g.getListFriends()));
			msg.put(LISTFILES, Utils.joinStrings(",",g.getListFiles()));
			msg.put(LISTOWNERS, arrayListFriendsToString(g.getListOwners()));
			msg.put("admin", g.getAdministrador());

			pnRTCClient.transmit(namefriend, msg);
		}catch(Exception e){

		}
	}
	private void handleNG(JSONObject grupojson){
		try{
			String nameGroup =(String)grupojson.get(NAMEGROUP);
			ArrayList<Friends> listFriends =stringtoArrayListFriend(grupojson.getString("listFriends"));
			int img =grupojson.getInt("imgGroup");
			ArrayList listFiles = new ArrayList();
			if (!grupojson.getString(LISTFILES).equals("")){
				listFiles = new ArrayList(Arrays.asList(grupojson.getString(LISTFILES).split(",")));
			}
			ArrayList<Friends> listOwners =new ArrayList<>();
			if(!grupojson.getString(LISTOWNERS).equals("")){
				listOwners = stringtoArrayListFriend(grupojson.getString(LISTOWNERS));
			}
			String admin =grupojson.getString("admin");
			Groups groupnew;
			if(grupojson.getString(LISTFILES).equals("") && grupojson.getString(LISTOWNERS).equals("")){
				 groupnew = new Groups(nameGroup, img, listFriends, admin);
			}else {
				 groupnew = new Groups(nameGroup, img, listFriends, listFiles, listOwners, admin);
			}

			if (mDatabaseHelper.existGroup(groupnew.nameGroup)){
				mDatabaseHelper.deleteGroup(groupnew.nameGroup, DatabaseHelper.GROUPS_TABLE_NAME);
			}
			if (listFiles.isEmpty()){
				mDatabaseHelper.addGroup(groupnew.getNameGroup(), arrayListFriendsToString(groupnew.getListFriends()), groupnew.getAdministrador());
				Toast.makeText(getApplicationContext(), "Has sido añadido al grupo " + nameGroup, Toast.LENGTH_SHORT).show();
			}else{
				mDatabaseHelper.addGroupComplete(groupnew.getNameGroup(), arrayListFriendsToString(groupnew.getListFriends()),Utils.joinStrings(",",groupnew.getListFiles()),arrayListFriendsToString(groupnew.getListOwners()), groupnew.getAdministrador());
				Toast.makeText(getApplicationContext(), "Has sido añadido al grupo " + nameGroup, Toast.LENGTH_SHORT).show();
			}
			//Intent intent = new Intent(Profile.this, listGroupsActivity.class);
			//intent.putExtra("username", username);
			//startActivityForResult(intent, 6);

		}catch (Exception e){

		}
	}
	private void DG(String namefriend, String nameGroup){
		try{
			JSONObject msg = new JSONObject();
			msg.put("type", "DG");
			msg.put(NAMEGROUP, nameGroup);
			pnRTCClient.transmit(namefriend, msg);

		}catch(Exception e){

		}
	}
	private void handleDG(JSONObject grupojson){
		try{
			String nameGroup =(String)grupojson.get(NAMEGROUP);
			//Groups groupnew = new Groups(nameGroup,img, listFriends,listFiles,listOwners,admin);

			if (mDatabaseHelper.existGroup(nameGroup)){
				mDatabaseHelper.deleteGroup(nameGroup, DatabaseHelper.GROUPS_TABLE_NAME);
				Toast.makeText(getApplicationContext(), "Has sido borrado del grupo " + nameGroup, Toast.LENGTH_SHORT).show();
			}
		}catch (Exception e){

		}
	}
	//pasar de un array lists de amigos a un string
	private String arrayListFriendsToString(ArrayList<Friends> listfriend) {
		String myString ="";
		for (int i = 0; i<listfriend.size();i++){
			if (myString.equals("")){
				myString=listfriend.get(i).getNombre();
				if (i < (listfriend.size() - 1)){myString = myString + ",";}
			}else {
				myString = myString + listfriend.get(i).getNombre();
				if (i < (listfriend.size() - 1)) {
					myString = myString + ",";
				}
			}
		}
		return myString;
	}
	@Override
	protected void onDestroy(){
		downloadService.stop();
		super.onDestroy();
	}
	@Override
	public void onBackPressed(){
		final Dialog d = new Dialog(Profile.this);
		d.setContentView(R.layout.dialog_exit);
		d.show();

		Button yes = d.findViewById(R.id.button_exit_yes);
		yes.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				d.dismiss();
				finish();
				System.exit(0);
			}
		});
		Button no = d.findViewById(R.id.button_exit_no);
		no.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				d.dismiss();
			}
		});
	}
}
