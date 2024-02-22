package com.ngobrol.ngobrolonlenrsa.EncryptionUtils;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigInteger;
import java.security.AlgorithmParameterGenerator;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.KeyAgreement;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AccKeyExchange extends AsyncTask<byte[], Integer, Void> {


    private PublicKey publicKey;
    private PrivateKey privateKey;

    private PublicKey peerPublicKey;

    private KeyFactory keyFactory;
    private KeyPair keyPair;
    private KeyAgreement keyAgreement;
    private KeyPairGenerator keyPairGenerator;
    private X509EncodedKeySpec x509EncodedKeySpec;

    private DHParameterSpec DHParam;
    private byte[] commonSecret;
    private SendKey sender;

    public AccKeyExchange(SendKey send) {
        this.sender = send;
    }


    //1 after receiving pk
    public void receiveParameter(String jsonString) {
//        String jsonString = "{\"p\":\"11401881534420482403651895398024722460029343210824796732561621186211250037348898085962991979413678696021072487967080694800041085953262428060482005694052783\",\"g\":\"8583832024054048083986210374373792129295247609543603927534743048494339647041504910304051281321393835490447438894954070712295342853104966171016979732866346\"}";

        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            String pString = jsonObject.getString("p");
            String gString = jsonObject.getString("g");
            Log.d("parsingaa p", "p: " + pString);
            Log.d("parsingaa g", "g: " + gString);

            // Create BigIntegers from the strings
            BigInteger p = new BigInteger(pString);
            BigInteger g = new BigInteger(gString);

            // Now you have the values of p and g
            Log.d("parsing p", "p: " + p);
            Log.d("parsing g", "g: " + g);

            DHParam = new DHParameterSpec(p, g);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    public PublicKey receivePublicKeyFromServer(byte[] publicKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
        Log.d("masuk receive", Encoding.base64Encode(publicKey));
        keyFactory = KeyFactory.getInstance("RSA");
        x509EncodedKeySpec = new X509EncodedKeySpec(publicKey);

        System.err.println("generating keys..");
        PublicKey peerPublicKey = keyFactory.generatePublic(x509EncodedKeySpec);
        Log.d("ini peerpub di funct", peerPublicKey.getEncoded().toString());
        return peerPublicKey;
    }

    //2
    public DHParameterSpec retrieveDHParamFromPB(PublicKey key){
        return ((DHPublicKey) key).getParams();
    }

    //3
    public void generateDHKeyPair() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
//        DHParameterSpec DHParam = retrieveDHParamFromPB(serverPublicKey);
//        Log.d("dhparam", DHParam.toString());

        keyPairGenerator = KeyPairGenerator.getInstance("DH");
        keyPairGenerator.initialize(DHParam);
        keyPair = keyPairGenerator.generateKeyPair();

        this.publicKey = keyPair.getPublic();
        this.privateKey = keyPair.getPrivate();

        try {
            initDHKeyAgreement();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
    }

    //3
    public void initiateRSAKeyPair() throws NoSuchAlgorithmException, InvalidParameterSpecException, InvalidAlgorithmParameterException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048); // You can change the key size here
        Log.d("apakah di sini", "boi2");
        keyPair = keyPairGenerator.generateKeyPair();

        Log.d("apakah di sini", "boi");
        this.publicKey = keyPair.getPublic();
        this.privateKey = keyPair.getPrivate();
        Log.d("apakah di sini", "boi3");
    }

    //4
    public void initDHKeyAgreement() throws NoSuchAlgorithmException, InvalidKeyException {
        this.privateKey = keyPair.getPrivate();
        Log.d("sudah dis1", "initdhagree");
        keyAgreement = KeyAgreement.getInstance("DH");
        Log.d("sudah dis2", "initdhagree");
        keyAgreement.init(privateKey);
        Log.d("sudah dis4", "initdhagree");
    }


    public void doPhase(PublicKey publicKey) throws InvalidKeyException {
        keyAgreement.doPhase(publicKey, true);
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public PublicKey getPeerPublicKey() {
        return peerPublicKey;
    }

    public SecretKeySpec getAESKey() {
        return Utils.generateAESKey(commonSecret);
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        sender.openDialogPublicKey();
    }

    @Override
    protected Void doInBackground(byte[]... bytes) {
        PublicKey publicKey;
        Log.d("ini", "doinbackgroundkeyexchage");
        try {
//            Utils.delay(5000);
            publishProgress(1);
            initiateRSAKeyPair();
            publishProgress(2);
//            receiveParameter(sender.recvParam());

            Log.d("kebekacc", "kabakacc");
            peerPublicKey = receivePublicKeyFromServer(sender.recvPublicKey());
            Log.d("diterimaacc", Encoding.base64Encode(peerPublicKey.getEncoded()));
            Log.d("kebekacc ini pubsendiri", this.publicKey.getEncoded().toString());
            sender.sendPublicKey(getPublicKey());
//            Utils.delay(5000);

            Log.d("hasilcommongeneracc", "selesai coy");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
        } catch (InvalidParameterSpecException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }
        return null;
    }


    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);
        if(values[0] == 1) sender.closeDialogPublicKey();
        if(values[0] == 2) sender.openDialogKeyPair();

    }

    @Override
    protected void onPostExecute(Void avoid) {
        super.onPostExecute(avoid);
//        Log.d("ini returnnya", Encoding.base64Encode(s.getEncoded()));
        sender.closeDialogKeyPair();
//        sender.assignPrivateKey(s);
        sender.finishExchange();
    }

    public interface SendKey {

        void openDialogPublicKey();
        void closeDialogPublicKey();

        void openDialogKeyPair();
        void closeDialogKeyPair();

        void sendPublicKey(PublicKey publicKey);

        String recvParam();
        byte[] recvPublicKey();

        void sendError(String error);
        void assignPrivateKey(SecretKeySpec s);

        void finishExchange();
    }

}
