package de.bitsharesmunich.graphenej;

import de.bitsharesmunich.graphenej.models.*;
import de.bitsharesmunich.graphenej.objects.Memo;
import com.google.common.primitives.UnsignedLong;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.reflect.TypeToken;
import de.bitsharesmunich.graphenej.errors.MalformedAddressException;
import de.bitsharesmunich.graphenej.errors.MalformedTransactionException;
import de.bitsharesmunich.graphenej.interfaces.WitnessResponseListener;
import de.bitsharesmunich.graphenej.test.NaiveSSLContext;
import com.neovisionaries.ws.client.*;
import de.bitsharesmunich.graphenej.api.*;
import org.bitcoinj.core.*;
import org.spongycastle.crypto.digests.RIPEMD160Digest;

import javax.net.ssl.SSLContext;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by nelson on 11/9/16.
 */
public class Test {

    public static final String WITNESS_URL = "api://api.devling.xyz:8088";
    public static final String OPENLEDGER_WITNESS_URL = "wss://bitshares.openledger.info/api";
    public static final String BLOCK_PAY_DE = "wss://de.blockpay.ch:8089";
    public static final String BLOCK_PAY_FR = "wss://fr.blockpay.ch:8089";

    private Transaction transaction;

    public Transaction GetTransaction() {
        return transaction;
    }

    private WitnessResponseListener mListener = new WitnessResponseListener() {

        @Override
        public void onSuccess(WitnessResponse response) {

            if (response.result.getClass() == AccountProperties.class) {
                AccountProperties accountProperties = (AccountProperties) response.result;
                System.out.println("Got account properties");
                System.out.println("account: " + accountProperties.toString());
                System.out.println("id: " + accountProperties.id);

            } else if (response.result.getClass() == ArrayList.class) {
                List list = (List) response.result;
                if (list.size() > 0) {
                    if (list.get(0) instanceof AccountProperties) {
                        List<AccountProperties> accountPropertiesList = list;
                        for (AccountProperties accountProperties : accountPropertiesList) {
                            System.out.println("Account id: " + accountProperties.id);
                        }
                    } else if (list.get(0) instanceof AssetAmount) {
                        AssetAmount assetAmount = (AssetAmount) list.get(0);
                        System.out.println("Got fee");
                        System.out.println("amount: " + assetAmount.getAmount() + ", asset id: " + assetAmount.getAsset().getObjectId());
                    } else if (list.get(0).getClass() == ArrayList.class) {
                        List sl = (List) list.get(0);
                        if (sl.size() > 0) {
                            if (response.result.getClass() == AccountProperties.class) {
                                AccountProperties accountProperties = (AccountProperties) response.result;
                                System.out.println("Got account properties " + accountProperties);
                            } else {
                                String accountId = (String) sl.get(0);
                                System.out.println("account id : " + accountId);
                                try {

                                    // Create a custom SSL context.
                                    SSLContext context = null;
                                    context = NaiveSSLContext.getInstance("TLS");
                                    WebSocketFactory factory = new WebSocketFactory();

                                    // Set the custom SSL context.
                                    factory.setSSLContext(context);

                                    WebSocket mWebSocket = factory.createSocket(OPENLEDGER_WITNESS_URL);
                                    ArrayList<UserAccount> userAccounts = new ArrayList<UserAccount>();
                                    userAccounts.add(new UserAccount(accountId));
                                    mWebSocket.addListener(new GetAccounts(userAccounts, null));
                                    mWebSocket.connect();
                                } catch (IOException e) {
                                    System.out.println("IOException. Msg: " + e.getMessage());
                                } catch (WebSocketException e) {
                                    System.out.println("WebSocketException. Msg: " + e.getMessage());
                                } catch (NoSuchAlgorithmException ex) {
                                    Logger.getLogger(Test.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            }
                        }
                    }
                } else {
                    System.out.println("Got empty list!");
                }
            } else if (response.result.getClass() == JsonArray.class) {
                System.out.println("Json array : " + ((JsonArray) response.result));
            } else {
                System.out.println("Got other: " + response.result.getClass());
            }
        }

        @Override
        public void onError(BaseResponse.Error error) {
            System.out.println("onError. message: " + error.message);
        }
    };

    public ECKey.ECDSASignature testSigning() {
        byte[] serializedTransaction = this.transaction.toBytes();
        Sha256Hash hash = Sha256Hash.wrap(Sha256Hash.hash(serializedTransaction));
        byte[] bytesDigest = hash.getBytes();
        ECKey sk = transaction.getPrivateKey();
        ECKey.ECDSASignature signature = sk.sign(hash);
        return signature;
    }

    public String testSigningMessage() {
        byte[] serializedTransaction = this.transaction.toBytes();
        Sha256Hash hash = Sha256Hash.wrap(Sha256Hash.hash(serializedTransaction));
        ECKey sk = transaction.getPrivateKey();
        return sk.signMessage(hash.toString());
    }

    public byte[] signMessage() {
        byte[] serializedTransaction = this.transaction.toBytes();
        Sha256Hash hash = Sha256Hash.wrap(Sha256Hash.hash(serializedTransaction));
        System.out.println(">> digest <<");
        System.out.println(Util.bytesToHex(hash.getBytes()));
        ECKey sk = transaction.getPrivateKey();
        System.out.println("Private key bytes");
        System.out.println(Util.bytesToHex(sk.getPrivKeyBytes()));
        boolean isCanonical = false;
        int recId = -1;
        ECKey.ECDSASignature sig = null;
        while (!isCanonical) {
            sig = sk.sign(hash);
            if (!sig.isCanonical()) {
                System.out.println("Signature was not canonical, retrying");
                continue;
            } else {
                System.out.println("Signature is canonical");
                isCanonical = true;
            }
            // Now we have to work backwards to figure out the recId needed to recover the signature.
            for (int i = 0; i < 4; i++) {
                ECKey k = ECKey.recoverFromSignature(i, sig, hash, sk.isCompressed());
                if (k != null && k.getPubKeyPoint().equals(sk.getPubKeyPoint())) {
                    recId = i;
                    break;
                } else {
                    if (k == null) {
                        System.out.println("Recovered key was null");
                    }
                    if (k.getPubKeyPoint().equals(sk.getPubKeyPoint())) {
                        System.out.println("Recovered pub point is not equal to sk pub point");
                    }
                }
            }
            if (recId == -1) {
                throw new RuntimeException("Could not construct a recoverable key. This should never happen.");
            }
        }
        int headerByte = recId + 27 + (sk.isCompressed() ? 4 : 0);
        byte[] sigData = new byte[65];  // 1 header + 32 bytes for R + 32 bytes for S
        sigData[0] = (byte) headerByte;
        System.arraycopy(Utils.bigIntegerToBytes(sig.r, 32), 0, sigData, 1, 32);
        System.arraycopy(Utils.bigIntegerToBytes(sig.s, 32), 0, sigData, 33, 32);
        System.out.println("recId: " + recId);
        System.out.println("r: " + Util.bytesToHex(sig.r.toByteArray()));
        System.out.println("s: " + Util.bytesToHex(sig.s.toByteArray()));
        return sigData;
//        return new String(Base64.encode(sigData), Charset.forName("UTF-8"));
    }

    public void testTransactionSerialization(long head_block_number, String head_block_id, long relative_expiration) {
        BlockData blockData = new BlockData(head_block_number, head_block_id, relative_expiration);

        ArrayList<BaseOperation> operations = new ArrayList<BaseOperation>();
        UserAccount from = new UserAccount("1.2.138632");
        UserAccount to = new UserAccount("1.2.129848");
        AssetAmount amount = new AssetAmount(UnsignedLong.valueOf(100), new Asset("1.3.120"));
        AssetAmount fee = new AssetAmount(UnsignedLong.valueOf(264174), new Asset("1.3.0"));
        operations.add(new TransferOperation(from, to, amount, fee));
        BrainKey brainKey = new BrainKey(Main.BILTHON_83_BRAIN_KEY, 0);
        this.transaction = new Transaction(brainKey.getWalletImportFormat(), blockData, operations);
        byte[] serializedTransaction = this.transaction.toBytes();
        System.out.println("Serialized transaction");
        System.out.println(Util.bytesToHex(serializedTransaction));
    }

    public void testCustomSerializer() {
        AssetAmount amount = new AssetAmount(UnsignedLong.valueOf(100), new Asset("1.3.120"));
        String jsonAmount = amount.toJsonString();
        System.out.println("JSON amount");
        System.out.println(jsonAmount);
    }

    public void testUserAccountSerialization() {
        UserAccount account = new UserAccount("1.2.138632");
        System.out.println(Util.bytesToHex(account.toBytes()));
    }

    public void testTransactionSerialization() {
        try {
            Transaction transaction = new TransferTransactionBuilder()
                    .setSource(new UserAccount("1.2.138632"))
                    .setDestination(new UserAccount("1.2.129848"))
                    .setAmount(new AssetAmount(UnsignedLong.valueOf(100), new Asset("1.3.120")))
                    .setFee(new AssetAmount(UnsignedLong.valueOf(264174), new Asset("1.3.0")))
                    .setPrivateKey(DumpedPrivateKey.fromBase58(null, Main.WIF).getKey())
                    .setBlockData(new BlockData(Main.REF_BLOCK_NUM, Main.REF_BLOCK_PREFIX, Main.RELATIVE_EXPIRATION))
                    .build();

            ArrayList<Serializable> transactionList = new ArrayList<>();
            transactionList.add(transaction);

            byte[] signature = transaction.getGrapheneSignature();
            System.out.println(Util.bytesToHex(signature));
            ApiCall call = new ApiCall(4, "call", "broadcast_transaction", transactionList, "2.0", 1);
            String jsonCall = call.toJsonString();
            System.out.println("json call");
            System.out.println(jsonCall);
        } catch (MalformedTransactionException e) {
            System.out.println("MalformedTransactionException. Msg: " + e.getMessage());
        }
    }

    public void testLoginSerialization() {
        ArrayList<Serializable> loginParams = new ArrayList<>();
//        loginParams.add("nelson");
//        loginParams.add("supersecret");
        loginParams.add(null);
        loginParams.add(null);
        ApiCall loginCall = new ApiCall(1, "login", loginParams, "2.0", 1);
        String jsonLoginCall = loginCall.toJsonString();
        System.out.println("login call");
        System.out.println(jsonLoginCall);
    }

    public void testNetworkBroadcastSerialization() {
        ArrayList<Serializable> params = new ArrayList<>();
        ApiCall networkParamsCall = new ApiCall(3, "network_broadcast", params, "2.0", 1);
        String call = networkParamsCall.toJsonString();
        System.out.println("network broadcast");
        System.out.println(call);
    }

    public void testNetworkBroadcastDeserialization() {
        String response = "{\"id\":2,\"result\":2}";
        Gson gson = new Gson();
        Type ApiIdResponse = new TypeToken<WitnessResponse<Integer>>() {
        }.getType();
        WitnessResponse<Integer> witnessResponse = gson.fromJson(response, ApiIdResponse);
    }

    public void testGetDynamicParams() {
        ArrayList<Serializable> emptyParams = new ArrayList<>();
        ApiCall getDynamicParametersCall = new ApiCall(0, "get_dynamic_global_properties", emptyParams, "2.0", 0);
        System.out.println(getDynamicParametersCall.toJsonString());
    }

    public void testRequiredFeesResponse() {
        String response = "{\"id\":1,\"result\":[{\"amount\":264174,\"asset_id\":\"1.3.0\"}]}";
        Type AccountLookupResponse = new TypeToken<WitnessResponse<List<AssetAmount>>>() {
        }.getType();
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(AssetAmount.class, new AssetAmount.AssetDeserializer());
        WitnessResponse<List<AssetAmount>> witnessResponse = gsonBuilder.create().fromJson(response, AccountLookupResponse);
        for (AssetAmount assetAmount : witnessResponse.result) {
            System.out.println("asset : " + assetAmount.toJsonString());
        }
    }

    public void testTransactionBroadcastSequence() {
        String url = Test.OPENLEDGER_WITNESS_URL;
        WitnessResponseListener listener = new WitnessResponseListener() {
            @Override
            public void onSuccess(WitnessResponse response) {
                System.out.println("onSuccess");
            }

            @Override
            public void onError(BaseResponse.Error error) {
                System.out.println("onError");
                System.out.println(error.data.message);
            }
        };

        try {
            ECKey from = new BrainKey(Main.BILTHON_83_BRAIN_KEY, 0).getPrivateKey();
            PublicKey to = new PublicKey(ECKey.fromPublicOnly(new BrainKey(Main.BILTHON_5_BRAIN_KEY, 0).getPublicKey()));

            // Creating memo
            long nonce = 1;
            byte[] encryptedMessage = Memo.encryptMessage(from, to, nonce, "another message");
            Memo memo = new Memo(new Address(ECKey.fromPublicOnly(from.getPubKey())), new Address(to.getKey()), nonce, encryptedMessage);

            // Creating transaction
            Transaction transaction = new TransferTransactionBuilder()
                    .setSource(new UserAccount("1.2.138632")) // bilthon-83
                    .setDestination(new UserAccount("1.2.139313")) // bilthon-5
                    .setAmount(new AssetAmount(UnsignedLong.valueOf(1), new Asset("1.3.0")))
                    .setFee(new AssetAmount(UnsignedLong.valueOf(264174), new Asset("1.3.0")))
                    .setPrivateKey(new BrainKey(Main.BILTHON_83_BRAIN_KEY, 0).getPrivateKey())
                    .setMemo(memo)
                    .build();

            ArrayList<Serializable> transactionList = new ArrayList<>();
            transactionList.add(transaction);

            SSLContext context = null;
            context = NaiveSSLContext.getInstance("TLS");
            WebSocketFactory factory = new WebSocketFactory();

            // Set the custom SSL context.
            factory.setSSLContext(context);

            WebSocket mWebSocket = factory.createSocket(BLOCK_PAY_DE);

            mWebSocket.addListener(new TransactionBroadcastSequence(transaction, new Asset("1.3.0"), listener));
            mWebSocket.connect();

        } catch (MalformedTransactionException e) {
            System.out.println("MalformedTransactionException. Msg: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("IOException. Msg: " + e.getMessage());
        } catch (WebSocketException e) {
            System.out.println("WebSocketException. Msg: " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            System.out.println("NoSuchAlgoritmException. Msg: " + e.getMessage());
        }
    }

    public void testAccountLookupDeserialization() {
        String response = "{\"id\":1,\"result\":[[\"ken\",\"1.2.3111\"],[\"ken-1\",\"1.2.101491\"],[\"ken-k\",\"1.2.108646\"]]}";
        Type AccountLookupResponse = new TypeToken<WitnessResponse<List<List<String>>>>() {
        }.getType();
        Gson gson = new Gson();
        WitnessResponse<List<List<String>>> witnessResponse = gson.fromJson(response, AccountLookupResponse);
        for (int i = 0; i < witnessResponse.result.size(); i++) {
            System.out.println("suggested name: " + witnessResponse.result.get(i).get(0));
        }
    }

    public void testPrivateKeyManipulations() {
        String brainKeyWords = "PUMPER ISOTOME SERE STAINER CLINGER MOONLIT CHAETA UPBRIM AEDILIC BERTHER NIT SHAP SAID SHADING JUNCOUS CHOUGH";
        BrainKey brainKey = new BrainKey(brainKeyWords, 0);

        ECKey privateKey = DumpedPrivateKey.fromBase58(null, brainKey.getWalletImportFormat()).getKey();
        System.out.println("private key..............: " + Util.bytesToHex(privateKey.getSecretBytes()));
        System.out.println("public key uncompressed..: " + Util.bytesToHex(privateKey.getPubKey()));
        System.out.println("public key compressed....: " + Util.bytesToHex(privateKey.getPubKeyPoint().getEncoded(true)));
        System.out.println("base58...................: " + Base58.encode(privateKey.getPubKeyPoint().getEncoded(true)));
        System.out.println("base58...................: " + Base58.encode(privateKey.getPubKey()));
    }

    public void testPublicKeyManipulations() {
//            PublicKey publicKey = new PublicKey("BTS8RiFgs8HkcVPVobHLKEv6yL3iXcC9SWjbPVS15dDAXLG9GYhnY");
//            System.out.println("Public key bytes");
//            System.out.println(Util.bytesToHex(publicKey.toBytes()));
        Address address = null;
        try {
            address = new Address("BTS8RiFgs8HkcVPVobHLKEv6yL3iXcC9SWjbPVS15dDAXLG9GYhnY");
            System.out.println("Public key");
            System.out.println(Util.bytesToHex(address.getPublicKey().toBytes()));
        } catch (MalformedAddressException e) {
            e.printStackTrace();
        }
    }

    public void testGetAccountByName() {

        WitnessResponseListener accountByNameListener = new WitnessResponseListener() {
            @Override
            public void onSuccess(WitnessResponse response) {
                System.out.println("onSuccess");
                AccountProperties accountProperties = (AccountProperties) response.result;
                System.out.println("Owner and active authorities are the same: "+accountProperties.active.equals(accountProperties.owner));
            }

            @Override
            public void onError(BaseResponse.Error error) {
                System.out.println("onError. Msg: "+error.message);
            }
        };

        try {
            SSLContext context = null;
            context = NaiveSSLContext.getInstance("TLS");
            WebSocketFactory factory = new WebSocketFactory();

            // Set the custom SSL context.
            factory.setSSLContext(context);

            WebSocket mWebSocket = factory.createSocket(BLOCK_PAY_DE);

            mWebSocket.addListener(new GetAccountByName("bilthon-83", accountByNameListener));
            mWebSocket.connect();
        } catch (IOException e) {
            System.out.println("IOException. Msg: " + e.getMessage());
        } catch (WebSocketException e) {
            System.out.println("WebSocketException. Msg: " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            System.out.println("NoSuchAlgorithmException. Msg: "+e.getMessage());
        }
    }

    public void testGetRequiredFees() {
        ArrayList<Serializable> accountParams = new ArrayList<>();
        Asset asset = new Asset("1.3.0");
        UserAccount from = new UserAccount("1.2.138632");
        UserAccount to = new UserAccount("1.2.129848");
        AssetAmount amount = new AssetAmount(UnsignedLong.valueOf(100), new Asset("1.3.120"));
        AssetAmount fee = new AssetAmount(UnsignedLong.valueOf(264174), new Asset("1.3.0"));
        TransferOperation transfer = new TransferOperation(from, to, amount, fee);
        ArrayList<BaseOperation> operations = new ArrayList<>();
        operations.add(transfer);

        accountParams.add(operations);
        accountParams.add(asset.getObjectId());

        try {
            WebSocketFactory factory = new WebSocketFactory().setConnectionTimeout(5000);
            WebSocket mWebSocket = factory.createSocket(WITNESS_URL);
            mWebSocket.addListener(new GetRequiredFees(operations, asset, mListener));
            mWebSocket.connect();
        } catch (IOException e) {
            System.out.println("IOException. Msg: " + e.getMessage());
        } catch (WebSocketException e) {
            System.out.println("WebSocketException. Msg: " + e.getMessage());
        }
    }

    /**
     * The final purpose of this test is to convert the plain brainkey at
     * Main.BILTHON_83_BRAIN_KEY into the WIF at Main.WIF
     */
    public void testBrainKeyOperations(boolean random) {
        try {
            BrainKey brainKey;
            if (random) {
                String current = new java.io.File(".").getCanonicalPath();
                File file = new File(current + "/src/main/java/com/luminiasoft/bitshares/brainkeydict.txt");

                BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
                StringBuffer buffer = new StringBuffer();
                String words = bufferedReader.readLine();
                String suggestion = BrainKey.suggest(words);
                brainKey = new BrainKey(suggestion, 0);
            } else {
                System.out.println("Using brain key: " + Main.BILTHON_5_BRAIN_KEY);
                brainKey = new BrainKey(Main.BILTHON_5_BRAIN_KEY, 0);

            }
            ECKey key = brainKey.getPrivateKey();
            System.out.println("Private key..................: " + Util.bytesToHex(key.getSecretBytes()));
            String wif = key.getPrivateKeyAsWiF(NetworkParameters.fromID(NetworkParameters.ID_MAINNET));
            System.out.println("Wif Compressed...............: " + wif);
            String wif2 = key.decompress().getPrivateKeyAsWiF(NetworkParameters.fromID(NetworkParameters.ID_MAINNET));
            System.out.println("Wif Decompressed.............: " + wif2);
            System.out.println("Wif from BrainKey............: " + brainKey.getWalletImportFormat());

            byte[] uncompressedPubKey = key.decompress().getPubKey();
            byte[] compressedPubKey = key.getPubKey();

            System.out.println("Public Key Decompressed......: " + Util.bytesToHex(uncompressedPubKey));
            System.out.println("Public Key Compressed........: " + Util.bytesToHex(compressedPubKey));

            // Address generation test
            Address address = new Address(ECKey.fromPublicOnly(key.getPubKey()));
            System.out.println("Block explorer's address.....: " + address);
        } catch (FileNotFoundException e) {
            System.out.println("FileNotFoundException. Msg: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("IOException. Msg: " + e.getMessage());
        }
    }

    public byte[] calculateChecksum(byte[] input) {
        byte[] answer = new byte[4];
        RIPEMD160Digest ripemd160Digest = new RIPEMD160Digest();
        ripemd160Digest.update(input, 0, input.length);
        byte[] output = new byte[160 / 8];
        ripemd160Digest.doFinal(output, 0);
        System.arraycopy(output, 0, answer, 0, 4);
        return answer;
    }

    public void testBip39Opertion() {
        BIP39 bip39 = new BIP39(Main.BIP39_KEY, "");
    }

    public void testAccountNamebyAddress() {
        WitnessResponseListener listener = new WitnessResponseListener() {
            @Override
            public void onSuccess(WitnessResponse response) {
                System.out.println("onSuccess");
            }

            @Override
            public void onError(BaseResponse.Error error) {
                System.out.println("onError");
            }
        };

        BrainKey brainKey = new BrainKey(Main.BILTHON_83_ORIGINAL_BRAIN_KEY, 0);
//        Address address = new Address(ECKey.fromPublicOnly(brainKey.getPrivateKey().getPubKey()));
        try {
            Address address = new Address("BTS5BgjNRDeawGSc1NPk91p2BYYEhJWKgsjLZGDmFgY6uwhAYLy9G");

            // Create a custom SSL context.
            SSLContext context = null;
            context = NaiveSSLContext.getInstance("TLS");
            WebSocketFactory factory = new WebSocketFactory();

            // Set the custom SSL context.
            factory.setSSLContext(context);

            WebSocket mWebSocket = factory.createSocket(BLOCK_PAY_DE);
            mWebSocket.addListener(new GetAccountsByAddress(address, listener));
            mWebSocket.connect();
        } catch (IOException e) {
            System.out.println("IOException. Msg: " + e.getMessage());
        } catch (WebSocketException e) {
            System.out.println("WebSocketException. Msg: " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            System.out.println("NoSuchAlgorithmException. Msg: " + e.getMessage());
        }
        catch (MalformedAddressException e) {
            System.out.println("MalformedAddressException. Msg: "+e.getMessage());
        }
    }

    public void testAccountNameById() {
        WitnessResponseListener listener = new WitnessResponseListener() {
            @Override
            public void onSuccess(WitnessResponse response) {
                System.out.println("onSuccess");
            }

            @Override
            public void onError(BaseResponse.Error error) {
                System.out.println("onError");
            }
        };

        try {
            // Create a custom SSL context.
            SSLContext context = null;
            context = NaiveSSLContext.getInstance("TLS");
            WebSocketFactory factory = new WebSocketFactory();

            // Set the custom SSL context.
            factory.setSSLContext(context);

            WebSocket mWebSocket = factory.createSocket(BLOCK_PAY_FR);
            ArrayList<UserAccount> userAccounts = new ArrayList<>();
            userAccounts.add(new UserAccount("1.2.138632"));
            mWebSocket.addListener(new GetAccounts(userAccounts, listener));
            mWebSocket.connect();
        } catch (IOException e) {
            System.out.println("IOException. Msg: " + e.getMessage());
        } catch (WebSocketException e) {
            System.out.println("WebSocketException. Msg: " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            System.out.println("NoSuchAlgorithmException. Msg: " + e.getMessage());
        }
    }

    public void testRelativeAccountHistory() {
        GetRelativeAccountHistory relativeAccountHistory = new GetRelativeAccountHistory(new UserAccount("1.2.138632"), mListener);
        try {
            // Create a custom SSL context.
            SSLContext context = null;
            context = NaiveSSLContext.getInstance("TLS");
            WebSocketFactory factory = new WebSocketFactory();

            // Set the custom SSL context.
            factory.setSSLContext(context);

            WebSocket mWebSocket = factory.createSocket(OPENLEDGER_WITNESS_URL);
            mWebSocket.addListener(relativeAccountHistory);
            mWebSocket.connect();
        } catch (IOException e) {
            System.out.println("IOException. Msg: " + e.getMessage());
        } catch (WebSocketException e) {
            System.out.println("WebSocketException. Msg: " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            System.out.println("NoSuchAlgorithmException. Msg: " + e.getMessage());
        }
    }

    public void testingInvoiceGeneration() {
        Invoice.LineItem[] lineItem = new Invoice.LineItem[]{new Invoice.LineItem("Apples", 2, "20 CSD")};
        Invoice invoice = new Invoice("bilthon-83", "Bilthon's store", "Invoice #12", "BTS", lineItem, "Thank you", "");
        String qrCodeData = Invoice.toQrCode(invoice);
        System.out.println("qrCodeData");
        System.out.println(qrCodeData);
        Invoice recovered = Invoice.fromQrCode(qrCodeData);
        System.out.println("recovered invoice: " + recovered.toJsonString());
    }

    public void testCompression() {
        String test = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";
        System.out.println("to compress");
        System.out.println(Util.bytesToHex(test.getBytes()));
        byte[] compressed = Util.compress(test.getBytes(), Util.XZ);
        System.out.println("compressed");
        System.out.println(Util.bytesToHex(compressed));
    }

    public void testCreateBinFile() {
        byte[] fileOutput = FileBin.getBytesFromBrainKey(Main.BILTHON_83_BRAIN_KEY, "123456", "bithon-83");
        ///String stringFile = "02f9f3eb0f61a0a96134975d86048bf92e114d6a1ce286140cad3a96c33e697282bc0a8a24d1ad0c7bc084a79816ce38e36bd2d624aa8bf686f53fb4c7e25e3974da9b40e0b17e9d0b5b82793a04b19646169c49c58cd67f4950aee7d275141dd24f52baaaee772995a9bd6a6562a7a38aae08951236d3f612aecef7aedd720a91eacbab3a792ca3ebe0105838fe11f6e9d0e83e5d77eb82f17c7ba85c670e69294a8bcf8365cfeca487a60093498496bbec394c729e3fda9f32fdccdea56288b36fb14a26aa309b548a6dd9c1d616d22167348f8d580f9dc7361b4457d2dc6d75ec985d8e2d3dcdff89cd425d9f14037ac961eb10ac5f92bab356ccecd8cf018ec05ab40d915b628a75ae32cfa4005634f08b24c0dc8c5a7636ed70cbd86a7f0c4f6236d74310470fafe3af8b5346c8cb61957f7292b468d276498f9e806399588b0afd5777e6ee5fe7cd3a6691d9b5486cb5c7adbd5ad0b17588dd32d82b01d49ecf0f2bf24ee54a490ee620e8ab049047ffa416b5efa8f1f0155d8f1be866a10d0d62ae44a3a8ecc0121c08837c2ee1a25f8b6dd7266273c41f4b9a5e3d600e3fb4de870f99ab1a7196d93f222595f92e97a2480f58b61b62639154a374b987664fd317622aaad156f831b03f2d9606537b65b3b1fcfb1fb6be39560ad2c301dd1fc25cee755e61b49ebfe42ca7e64b4b0fc4aa347b48a85c0b585a3499fe278e25cb2141f8009b9afc875fa2a2c439bf6cdec4b5190a6deb7f9390f072beb24749a8a2114cc1870c07be079abb3ee0ebc827f9b53e158a529bc6552eba280f05edf5f7ae1911de7acb4888150a509d029ec7c9da6de8adabbca6773a0a293a0a42de8278c82e88b9390b42b56f58bd8633fb97130e799a47a744e2e8958fd5";
        //fileOutput = new BigInteger(stringFile, 16).toByteArray();
        System.out.println(FileBin.getBrainkeyFromByte(fileOutput, "123456"));
    }

    public void testImportBinFile() {
        try {
            String current = new File(".").getCanonicalPath();
            File file = new File(current + "/src/main/java/de/bitsharesmunich/graphenej/bts_bilthon_20161218.bin");
            Path path = Paths.get(file.getAbsolutePath());
            byte[] data = Files.readAllBytes(path);

            System.out.println(FileBin.getBrainkeyFromByte(data, "123456"));
        } catch (IOException e) {
            System.out.println("IOException while trying to open bin file. Msg: "+e.getMessage());
        }
    }

    public void testAccountUpdateSerialization() {
        String newAddress = "BTS8RiFgs8HkcVPVobHLKEv6yL3iXcC9SWjbPVS15dDAXLG9GYhnY";
        try {
            Address address = new Address(newAddress);
            HashMap<PublicKey, Integer> authMap = new HashMap<>();
            authMap.put(address.getPublicKey(), 1);
            Authority authority = new Authority(1, authMap, null);
            AccountOptions options = new AccountOptions(address.getPublicKey());
            BrainKey brainKey = new BrainKey(Main.BILTHON_83_BRAIN_KEY, 0);
            Transaction transaction = new AccountUpdateTransactionBuilder(brainKey.getPrivateKey())
                    .setAccont(new UserAccount("1.2.140994"))
//                    .setOwner(authority)
                    .setActive(authority)
                    .setOptions(options)
                    .setBlockData(new BlockData(Main.REF_BLOCK_NUM, Main.REF_BLOCK_PREFIX, Main.RELATIVE_EXPIRATION))
                    .build();

            System.out.println("Json object");
            System.out.println(transaction.toJsonString());
            System.out.println("Serialized transaction");
            System.out.println(Util.bytesToHex(transaction.toBytes()));
        } catch (MalformedAddressException e) {
            System.out.println("MalformedAddressException. Msg: " + e.getMessage());
        } catch (MalformedTransactionException e) {
            System.out.println("MalformedTransactionException. Msg: " + e.getMessage());
        }
    }

    public void testAccountUpdateOperationBroadcast() {

        WitnessResponseListener listener = new WitnessResponseListener() {
            @Override
            public void onSuccess(WitnessResponse response) {
                System.out.println("onSuccess");
            }

            @Override
            public void onError(BaseResponse.Error error) {
                System.out.println("onError");
            }
        };

        String newAddress = "BTS8RiFgs8HkcVPVobHLKEv6yL3iXcC9SWjbPVS15dDAXLG9GYhnY";
        try {
            Address address = new Address(newAddress);
            HashMap<PublicKey, Integer> authMap = new HashMap<>();
            authMap.put(address.getPublicKey(), 1);
            Authority authority = new Authority(1, authMap, null);
            AccountOptions options = new AccountOptions(address.getPublicKey());
            BrainKey brainKey = new BrainKey(Main.BILTHON_83_BRAIN_KEY, 0);
            Transaction transaction = new AccountUpdateTransactionBuilder(brainKey.getPrivateKey())
                    .setAccont(new UserAccount("1.2.138632"))
//                    .setOwner(authority)
                    .setActive(authority)
                    .setOptions(options)
                    .build();

            SSLContext context = null;
            context = NaiveSSLContext.getInstance("TLS");
            WebSocketFactory factory = new WebSocketFactory();

            // Set the custom SSL context.
            factory.setSSLContext(context);

            WebSocket mWebSocket = factory.createSocket(BLOCK_PAY_DE);

            mWebSocket.addListener(new TransactionBroadcastSequence(transaction, new Asset("1.3.0"), listener));
            mWebSocket.connect();

        } catch (MalformedAddressException e) {
            System.out.println("MalformedAddressException. Msg: " + e.getMessage());
        } catch (MalformedTransactionException e) {
            System.out.println("MalformedTransactionException. Msg: " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            System.out.println("NoSuchAlgorithmException. Msg: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("IOException. Msg: " + e.getMessage());
        } catch (WebSocketException e) {
            System.out.println("WebSocketException. Msg: " + e.getMessage());
        }
    }

    public void testLookupAccounts() {
        WitnessResponseListener listener = new WitnessResponseListener() {
            @Override
            public void onSuccess(WitnessResponse response) {
                System.out.println("onSuccess");
            }

            @Override
            public void onError(BaseResponse.Error error) {
                System.out.println("onError");
            }
        };

        SSLContext context = null;
        try {
            context = NaiveSSLContext.getInstance("TLS");
            WebSocketFactory factory = new WebSocketFactory();

            // Set the custom SSL context.
            factory.setSSLContext(context);

            WebSocket mWebSocket = factory.createSocket(BLOCK_PAY_DE);

            mWebSocket.addListener(new LookupAccounts("bilthon", listener));
            mWebSocket.connect();

        } catch (NoSuchAlgorithmException e) {
            System.out.println("NoSuchAlgorithmException. Msg: " + e.getMessage());
        } catch (WebSocketException e) {
            System.out.println("WebSocketException. Msg: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("IOException. Msg: " + e.getMessage());
        }
    }

    public void testDecodeMemo() {

        ECKey from = new BrainKey(Main.BILTHON_83_BRAIN_KEY, 0).getPrivateKey();
        PublicKey to = new PublicKey(ECKey.fromPublicOnly(new BrainKey(Main.BILTHON_5_BRAIN_KEY, 0).getPublicKey()));

//        Memo sendMemo = new MemoBuilder().setFromKey(from).setToKey(to).setMessage("test message").build();

//        JsonElement memoJson = sendMemo.toJsonObject();
//        System.out.println("generated Json : " + memoJson.toString());
//        System.out.println("Decode Memo : " + Memo.decryptMessage(from, to, memoJson.getAsJsonObject().get("message").getAsString(), memoJson.getAsJsonObject().get("nonce").getAsString()));
    }

    public void testGetRelativeAccountHistory(){
        WitnessResponseListener listener = new WitnessResponseListener() {
            @Override
            public void onSuccess(WitnessResponse response) {
                System.out.println("onSuccess");
                List<HistoricalTransfer> transactionHistory = (List<HistoricalTransfer>) response.result;
                System.out.println("Number of transactions: "+transactionHistory.size());
                for(HistoricalTransfer historical : transactionHistory){
                    if(historical.getOperation() != null){
                        TransferOperation op = historical.getOperation();
                        System.out.println("from: "+op.getFrom().getObjectId()+", to: "+op.getTo().getObjectId()+", amount: "+op.getAssetAmount().getAmount()+", block #: "+historical.getBlockNum());
                    }
                }
            }

            @Override
            public void onError(BaseResponse.Error error) {
                System.out.println("onError");
            }
        };

        SSLContext context = null;
        try {
            context = NaiveSSLContext.getInstance("TLS");
            WebSocketFactory factory = new WebSocketFactory();

            // Set the custom SSL context.
            factory.setSSLContext(context);

            WebSocket mWebSocket = factory.createSocket(BLOCK_PAY_DE);

            mWebSocket.addListener(new GetRelativeAccountHistory(new UserAccount("1.2.140994"), listener));
            mWebSocket.connect();

        } catch (NoSuchAlgorithmException e) {
            System.out.println("NoSuchAlgorithmException. Msg: " + e.getMessage());
        } catch (WebSocketException e) {
            System.out.println("WebSocketException. Msg: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("IOException. Msg: " + e.getMessage());
        }
    }

    public void testLookupAssetSymbols(){
        WitnessResponseListener listener = new WitnessResponseListener() {
            @Override
            public void onSuccess(WitnessResponse response) {
                System.out.println("onSuccess");
                WitnessResponse<List<Asset>> resp = response;
                for(Asset asset : resp.result){
                    System.out.println("Asset: "+asset.getObjectId()+", Symbol: "+asset.getSymbol()+", supply: ");
                }
            }

            @Override
            public void onError(BaseResponse.Error error) {
                System.out.println("onError");
            }
        };

        SSLContext context = null;
        try {
            context = NaiveSSLContext.getInstance("TLS");
            WebSocketFactory factory = new WebSocketFactory();

            // Set the custom SSL context.
            factory.setSSLContext(context);

            WebSocket mWebSocket = factory.createSocket(BLOCK_PAY_DE);

            ArrayList<Asset> assets = new ArrayList<>();
            assets.add(new Asset("1.3.0"));
            assets.add(new Asset("1.3.120"));
            assets.add(new Asset("1.3.121"));
            mWebSocket.addListener(new LookupAssetSymbols(assets, listener));
            mWebSocket.connect();

        } catch (NoSuchAlgorithmException e) {
            System.out.println("NoSuchAlgorithmException. Msg: " + e.getMessage());
        } catch (WebSocketException e) {
            System.out.println("WebSocketException. Msg: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("IOException. Msg: " + e.getMessage());
        }
    }

    public void testGetBlockHeader(){
        WitnessResponseListener listener = new WitnessResponseListener() {
            @Override
            public void onSuccess(WitnessResponse response) {
                System.out.println("onSuccess");
            }

            @Override
            public void onError(BaseResponse.Error error) {
                System.out.println("onError");
            }
        };

        SSLContext context = null;
        try {
            context = NaiveSSLContext.getInstance("TLS");
            WebSocketFactory factory = new WebSocketFactory();

            // Set the custom SSL context.
            factory.setSSLContext(context);

            WebSocket mWebSocket = factory.createSocket(BLOCK_PAY_DE);


            mWebSocket.addListener(new GetBlockHeader(11989481, listener));
            mWebSocket.connect();

        } catch (NoSuchAlgorithmException e) {
            System.out.println("NoSuchAlgorithmException. Msg: " + e.getMessage());
        } catch (WebSocketException e) {
            System.out.println("WebSocketException. Msg: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("IOException. Msg: " + e.getMessage());
        }
    }

    void testGetLimitOrders() {
        SSLContext context = null;
        try {
            context = NaiveSSLContext.getInstance("TLS");
            WebSocketFactory factory = new WebSocketFactory();

            // Set the custom SSL context.
            factory.setSSLContext(context);

            WebSocket mWebSocket = factory.createSocket(BLOCK_PAY_DE);

            mWebSocket.addListener(new GetLimitOrders("1.3.562", "1.3.0", 100, new WitnessResponseListener() {
                @Override
                public void onSuccess(WitnessResponse response) {
                    List<LimitOrder> orders = (List<LimitOrder>) response.result;
                    for(LimitOrder order : orders){
                        System.out.println(String.format("OBITS: %d, BTS: %d", order.sell_price.base.getAmount().longValue(), order.sell_price.quote.getAmount().longValue()));
                        double price = (((double) order.sell_price.quote.getAmount().longValue()) / ((double) order.sell_price.base.getAmount().longValue()));
                        System.out.println(String.format("Selling %s for %s at %f %s/%s, expiration: %s",
                                order.sell_price.base.getAsset().getObjectId(),
                                order.sell_price.quote.getAsset().getObjectId(),
                                price,
                                order.sell_price.base.getAsset().getObjectId(),
                                order.sell_price.quote.getAsset().getObjectId(),
                                order.expiration));
                    }
                }

                @Override
                public void onError(BaseResponse.Error error) {
                    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                }
            }));
            mWebSocket.connect();

        } catch (NoSuchAlgorithmException e) {
            System.out.println("NoSuchAlgorithmException. Msg: " + e.getMessage());
        } catch (WebSocketException e) {
            System.out.println("WebSocketException. Msg: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("IOException. Msg: " + e.getMessage());
        }
    }

    void testGetTradeHistory() {
        SSLContext context = null;
        try {
            context = NaiveSSLContext.getInstance("TLS");
            WebSocketFactory factory = new WebSocketFactory();

            // Set the custom SSL context.
            factory.setSSLContext(context);

            WebSocket mWebSocket = factory.createSocket(BLOCK_PAY_FR);

            Calendar from = Calendar.getInstance();
            from.roll(Calendar.DAY_OF_MONTH, false);
            from.roll(Calendar.DAY_OF_MONTH, false);
            Calendar to = Calendar.getInstance();
            to.roll(Calendar.DAY_OF_MONTH, false);

            mWebSocket.addListener(new GetTradeHistory("BTS", "EUR", "20161215T0130000", "20161212T233000",100, new WitnessResponseListener() {
                @Override
                public void onSuccess(WitnessResponse response) {
                    List<MarketTrade> orders = (List<MarketTrade>) response.result;
                    for(MarketTrade markeTrade : orders){
                        System.out.println("At " + markeTrade.date + " amount " + markeTrade.amount + " value " + markeTrade.value + " price " + markeTrade.price);
                    }
                }

                @Override
                public void onError(BaseResponse.Error error) {
                }
            }));
            mWebSocket.connect();

        } catch (NoSuchAlgorithmException e) {
            System.out.println("NoSuchAlgorithmException. Msg: " + e.getMessage());
        } catch (WebSocketException e) {
            System.out.println("WebSocketException. Msg: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("IOException. Msg: " + e.getMessage());
        }
    }

    public void testGetMarketHistory(){
        SSLContext context = null;

        WitnessResponseListener listener = new WitnessResponseListener() {
            @Override
            public void onSuccess(WitnessResponse response) {
                System.out.println("onSuccess");
                List<BucketObject> bucketList = (List<BucketObject>) response.result;
                if(bucketList.size() > 0){
                    BucketObject bucket = bucketList.get(0);
                    Asset base = bucket.key.base;
                    Asset quote = bucket.key.quote;
                    base.setPrecision(5);
                    quote.setPrecision(4);
                    System.out.println(String.format("Base. symbol: %s, precision: %d", base.getObjectId(), base.getPrecision()));
                    System.out.println(String.format("Quote. symbol: %s, precision: %d", quote.getObjectId(), quote.getPrecision()));
                    Converter converter = new Converter(base, quote, bucket);
                    double rate = converter.getConversionRate(Converter.CLOSE_VALUE, Converter.BASE_TO_QUOTE);
                    System.out.println(String.format("Conversion rate is 1 base -> %f quote", rate));
                    double rate2 = converter.getConversionRate(Converter.CLOSE_VALUE, Converter.QUOTE_TO_BASE);
                    System.out.println(String.format("Conversion rate is 1 quote -> %f base", rate2));
                }
            }

            @Override
            public void onError(BaseResponse.Error error) {
                System.out.println("onError");
            }
        };

        try {
            context = NaiveSSLContext.getInstance("TLS");
            WebSocketFactory factory = new WebSocketFactory();

            // Set the custom SSL context.
            factory.setSSLContext(context);

            WebSocket mWebSocket = factory.createSocket(BLOCK_PAY_FR);

            long posixInstant = 1482436057000l;
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(posixInstant);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MINUTE, 0);

            Asset USD = new Asset("1.3.121", "USD", 4);
            Asset BTS = new Asset("1.3.0", "BTS", 5);
            long bucket = 3600;

            mWebSocket.addListener(new GetMarketHistory(BTS, USD, bucket, cal.getTime(), cal.getTime(), listener));
            mWebSocket.connect();
        } catch (NoSuchAlgorithmException e) {
            System.out.println("NoSuchAlgorithmException. Msg: " + e.getMessage());
        } catch (WebSocketException e) {
            System.out.println("WebSocketException. Msg: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("IOException. Msg: " + e.getMessage());
        }
    }

    public void testAssetSerialization(){
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutput out = new DataOutputStream(byteArrayOutputStream);
        try {
            Varint.writeUnsignedVarLong(120, out);
        } catch (IOException e) {
            e.printStackTrace();
        }
        byte[] bytes = byteArrayOutputStream.toByteArray();
        System.out.println("serialized: "+Util.bytesToHex(bytes));
    }
}
