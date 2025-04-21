# GeoIP

A service providing IP geolocation, written in Kotlin. Hardcoded to use GeoLite2-City. Simple bearer auth API with live config reloading.

The server logs all successful lookups and all errors.

## Usage

### Building

Build this how you would any other Gradle project:
```bash
./gradlew build
```
Run it like so:
```bash
java -jar build/libs/GeoIP-1.3.jar
```
The jar binds to port 8080 by default, which can be changed in the config file.

### Configuration

The jar expects two files called `GeoLite2-City.mmdb` and `config.yaml` to be present in the shell's current directory.

Download the MaxMind database and save it as `GeoLite2-City.mmdb`. See [MaxMind's docs](https://dev.maxmind.com/geoip/updating-databases/) for help. Create a new file called `config.yaml`.

This is the minimum required in `config.yaml`:
```yaml
api_key_hashes:
  - SHA-256 sum of an API key
```

You can also copy `example.config.yaml` to `config.yaml` and customize it.

### Calling the API

Let's use the IP address of one of Mullvad's servers as an example. Make a `GET` request to the `/geo` route like so:
```bash
curl -H "Authorization: Bearer my-secure-api-key" \
http://localhost:8080/geo?ip=87.249.134.1
```
This returns a JSON document:
```json
{
  "ip": "87.249.134.1",
  "country": "United States",
  "city": "Chicago",
  "mostSpecificSubdivision": "Illinois",
  "postalCode": "60602",
  "location": {
    "lat": 41.8835,
    "lon": -87.6305,
    "accuracyRadius": 20,
    "timeZone": "America/Chicago"
  }
}

```
All fields except `ip` can be null.

`mostSpecificSubdivision` is the most precise subdivision applicable, depending on the country. For example, it's a state for an IP in the US, but it might be a county for an IP in England.

`accuracyRadius` is the radius in kilometers from the latitude and longitude that the IP address is likely inside.

Making a `POST` request to the `/reload` route will reload `config.yaml`, updating the list of valid key hashes. The `/reload` route requires the admin key. The admin key is not allowed access to the `/geo` route.

Make a `POST` request to `/reload` like so:
```bash
curl -X POST -H "Authorization: Bearer my-admin-key" \
http://localhost:8080/reload
```

This returns a JSON document with the number of loaded keys:
```json
{
  "keys_loaded": 1
}

```
