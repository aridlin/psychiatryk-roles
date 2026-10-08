package pl.aridlin.psychiatrykroles.runtime;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Server-only persistent key. Clients receive signed items, never this key. */
public final class VariantSignature {
    private static final Path FILE=Path.of("config/psychiatryk-runtime-secret.key");
    private static byte[] secret;
    private static synchronized byte[] key(){
        if(secret!=null)return secret;
        try{
            Files.createDirectories(FILE.getParent());
            if(!Files.exists(FILE)){
                byte[] generated=new byte[32];new SecureRandom().nextBytes(generated);
                try{Files.write(FILE,generated,StandardOpenOption.CREATE_NEW);}
                catch(FileAlreadyExistsException raced){/* Read the winner's key. */}
                if(Files.getFileStore(FILE).supportsFileAttributeView("posix"))Files.setPosixFilePermissions(FILE,PosixFilePermissions.fromString("rw-------"));
            }
            byte[] saved=Files.readAllBytes(FILE);
            if(saved.length!=32)throw new IllegalStateException("Invalid runtime key length");
            return secret=saved;
        }catch(Exception ex){throw new IllegalStateException("Runtime item key unavailable",ex);}
    }
    public static String sign(String id,String base){
        try{Mac h=Mac.getInstance("HmacSHA256");h.init(new SecretKeySpec(key(),"HmacSHA256"));
            return HexFormat.of().formatHex(h.doFinal((id+"\u0000"+base).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }catch(GeneralSecurityException ex){throw new IllegalStateException(ex);}
    }
    public static boolean verify(String id,String base,String signature){
        if(signature==null||signature.length()!=64)return false;
        try{return MessageDigest.isEqual(HexFormat.of().parseHex(sign(id,base)),HexFormat.of().parseHex(signature));}
        catch(RuntimeException invalid){return false;}
    }
}
