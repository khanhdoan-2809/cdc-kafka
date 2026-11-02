# Generate RSA Public/Private Key Pair
## Step 1: Generate private key (PKCS#8 format)
```
openssl genrsa -out private.pem 2048
openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt -in private.pem -out private-key.pem
```

## Step 2: Generate public key from private key
```
openssl rsa -pubout -in private.pem -out public.pem
```

## Step 3:
Put `public.pem` into `src/main/resources/` of Resource Server

## Step 4: Configure `application.yml`
```
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          public-key-location: classpath:public.pem

jwt:
  private-key: classpath:private-key.pem  # Dùng để sign token
  expiration: 3600000  # 1 hour
  ```