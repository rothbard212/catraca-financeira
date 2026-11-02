package com.rothbard212.catraca_financeira.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class AssinaturaMercadoPago {

    private final String segredo;

    public AssinaturaMercadoPago(@Value("${mercadopago.webhook-secret}") String segredo) {
        this.segredo = segredo;
    }

    public boolean valida(String xSignature, String xRequestId, String dataId) {
        if (xSignature == null) return false;

        String ts = null, v1 = null;
        for (String parte : xSignature.split(",")) {
            String[] kv = parte.trim().split("=", 2);
            if (kv.length != 2) continue;
            if (kv[0].equals("ts")) ts = kv[1];
            if (kv[0].equals("v1")) v1 = kv[1];
        }
        if (ts == null || v1 == null) return false;

        StringBuilder manifesto = new StringBuilder();
        if (dataId != null) manifesto.append("id:").append(dataId.toLowerCase()).append(";");
        if (xRequestId != null) manifesto.append("request-id:").append(xRequestId).append(";");
        manifesto.append("ts:").append(ts).append(";");

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String esperado = HexFormat.of().formatHex(
                    mac.doFinal(manifesto.toString().getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(
                    esperado.getBytes(StandardCharsets.UTF_8), v1.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            return false;
        }
    }
}
