# Comandos do projeto

## Backend Spring Boot

O projeto usa Spring Boot 3, portanto o Maven precisa rodar com Java 17 ou superior.

No PowerShell, ajuste o `JAVA_HOME` antes do build:

```powershell
$env:JAVA_HOME = "C:\caminho\para\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
mvn -q -DskipTests compile
mvn spring-boot:run
```

Como contorno temporario para o erro `PKIX path building failed`, o projeto inclui:

- `.mvn/maven.config`
- `.mvn/settings.xml`

O `maven.config` aplica estas opcoes:

```powershell
-Dmaven.wagon.http.ssl.insecure=true
-Dmaven.wagon.http.ssl.allowall=true
-Dmaven.wagon.http.ssl.ignore.validity.dates=true
```

Tambem aponta para um espelho temporario em `.mvn/settings.xml`.

Se quiser executar sem depender do arquivo `.mvn/maven.config`, use:

```powershell
mvn -s .mvn/settings.xml -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true -DskipTests compile
```

## Frontend Angular

```powershell
cd frontend
npm install
npm start
```

Se o NPM tambem falhar por certificado SSL:

```powershell
npm config set strict-ssl false
npm install
npm start
```

Depois acesse:

```text
http://localhost:4200
```

## Endpoints

Buscar voos:

```http
POST http://localhost:8080/api/voos/buscar
```

Body:

```json
{
  "origem": "GRU",
  "destino": "SDU",
  "data": "2026-10-15"
}
```

Exportar Excel:

```http
GET http://localhost:8080/api/voos/exportar-excel
```
