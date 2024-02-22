package com.ngobrol.ngobrolonlenrsa.EncryptionUtils;

import android.os.AsyncTask;
import android.util.Log;

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

public class KeyExchange extends AsyncTask<byte[], Integer, Void> {


    private PublicKey publicKey;
    private PrivateKey privateKey;

    private PublicKey peerPublicKey;

    private KeyFactory keyFactory;
    private KeyPair keyPair;
    private KeyAgreement keyAgreement;
    private KeyPairGenerator keyPairGenerator;
    private X509EncodedKeySpec x509EncodedKeySpec;

    private byte[] commonSecret;
    private SendKey sender;

    public KeyExchange(SendKey send) {
        this.sender = send;
    }


    //1 after receiving pk
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
    public void generateDHKeyPair(PublicKey serverPublicKey) throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        DHParameterSpec DHParam = retrieveDHParamFromPB(serverPublicKey);

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
//            sender.sendParam(getParam());
            sender.sendPublicKey(getPublicKey());
            Log.d("kebek", "kabak");
//            kodok = sender.recvPublicKey();
//            Log.d("acumalaka", kodok.toString());
            peerPublicKey = receivePublicKeyFromServer(sender.recvPublicKey());
            Log.d("diterima", Encoding.base64Encode(peerPublicKey.getEncoded()));
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

        void sendParam(DHParameterSpec DHParam);


        byte[] recvPublicKey();
        void sendError(String error);
        void assignPrivateKey(SecretKeySpec s);

        void finishExchange();
    }

}
