# Tartarus Coordinator

SpringBoot + Kotlin.

# Install

```
brew install mosquito # for mqtt CLI tools on osx
```

# Unit Testing

```
./gradlew test
```

# Building

```
./gradlew bootJar
```

# OpenSSL related

Making a simple keypair

```
# Generate private key and save to file
openssl ecparam -name prime256v1 -genkey -noout -out ec_private_key.pem

# Extract matching public key and save to file
openssl ec -in ec_private_key.pem -pubout -out ec_public_key.pem

# Output base64 (single line) versions
openssl ec -in ec_private_key.pem -outform DER | base64 -w0 > ec_private_key.b64
openssl ec -in ec_private_key.pem -pubout -outform DER | base64 -w0 > ec_public_key.b64
```