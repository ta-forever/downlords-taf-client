# Encrypted-demo test fixtures

Two binaries used by `LocalDemoSealServiceTest`:

| file | what it is |
|---|---|
| `sealed_demo.bin` | a demo left **sealed**, exactly as a TotalA.exe crash would leave it |
| `expected_plain.bin` | what `sealed_demo.bin` must decrypt to, byte for byte |

## Why they are committed rather than generated

They were produced by the **Delphi recorder itself** — `tadr.git`
`src/dplayx/tests/sealtest.dpr` — not by any Java code. That is the entire point.

`LocalDemoSealService` carries a hand-written ChaCha20 and a set of hardcoded preamble
offsets. A test that encrypted with that same code and then decrypted with it would agree
with itself no matter how wrong both halves were. Checking against an artifact of a
*different* implementation is what actually pins the ChaCha20 port, the 326-byte preamble
layout and the body-relative keystream offset.

They cannot be regenerated on demand: producing one needs Windows, Delphi 7 and the
recorder tree. No CI job and no Linux dev box can rebuild them, so committing is the only
way this test runs anywhere but the machine that made them.

## They contain no real game data

`sealtest.dpr` writes the `TA Demo` magic followed by deterministic filler
(`Char((Length(s) * 7 + i) and $FF)`), not a recording of a real match. There are no player
names, no map name, and **no `PLAYER_ADDR` sector** — a genuine `.tad` embeds player IP
addresses there, which is why a real demo should never be used as a fixture.

Nor is the key in the test secret. `KEY_BASE64` is the ChaCha20 key for this synthetic
file; publishing it beside the seal is one known plaintext/ciphertext pair under a *public*
key, which anyone can generate at will. It reveals nothing about the private key, and `z` is
not recoverable from `K` through SHA-256.

## When they need regenerating

Only when the on-disk format changes — i.e. `DEMO_ENC_FORMAT_VER` in
`tadr.git src/dplayx/log2.pas` is bumped, or the preamble layout moves. The test will fail
with a version mismatch, which is the intended signal, not a flaky test.

## How to regenerate (Windows + Delphi 7)

```powershell
# 1. build and run the recorder's own seal test; it deliberately leaks its writer so the
#    demo is left sealed rather than decrypted at exit
$b='D:\wrk\tadrtest'; $s='D:\wrk\tadr.git\src\dplayx'
New-Item -ItemType Directory -Force $b | Out-Null
Copy-Item -Force "$s\tests\*" $b
Set-Location $b
$d='C:\Program Files (x86)\Borland\Delphi7\Bin\dcc32.exe'
& $d -B "-U$s;$s\TAMem;$s\plugins;$s\packets;$s\netmsgHandling" "-I$s" `
     -DIsTesting -DModEscalation sealtest.dpr
.\sealtest.exe        # -> sealtest_demo.bin, sealtest_plain.bin
```

```python
# 2. derive the ChaCha20 key from the seal, using the demo-seal PRIVATE key
#    (this is what faf-api's POST /demo-seal does)
import hashlib, struct, subprocess, re, base64
raw = open("sealtest_demo.bin", "rb").read()
FMT = "<8sHH12sIq32sH256s"
magic, ver, flags, nonce, dpid, utc, pname, seal_len, seal = struct.unpack(
    FMT, raw[:struct.calcsize(FMT)])
out = subprocess.run(["openssl", "rsa", "-in", r"D:\wrk\faf\demoseal\taf_demoseal_priv.pem",
                      "-noout", "-text"], capture_output=True, text=True, check=True).stdout
def grab(label, nxt):
    m = re.search(label + r":\s*\n(.*?)(?=\n(?:" + "|".join(nxt) + r"))", out, re.S)
    return int(re.sub(r"[^0-9a-f]", "", m.group(1)), 16)
n, d = grab("modulus", ["publicExponent"]), grab("privateExponent", ["prime1"])
z = pow(int.from_bytes(seal, "big"), d, n)
K = hashlib.sha256(z.to_bytes(256, "big") + struct.pack("<I", dpid)).digest()
print("EXPECTED_DPID =", "%08x" % dpid)
print("KEY_BASE64    =", base64.b64encode(K).decode())
```

```
# 3. copy in and update the test's two constants
sealtest_demo.bin  -> src/test/resources/demoseal/sealed_demo.bin
sealtest_plain.bin -> src/test/resources/demoseal/expected_plain.bin
then set EXPECTED_DPID and KEY_BASE64 in LocalDemoSealServiceTest.
```

`z` is random per run, so a regenerated fixture always needs a fresh `KEY_BASE64`. The dpid
is hardcoded in `sealtest.dpr` (`$03bf990e`) and only changes if you edit that file.

See `NETWORK_INTEL_MITIGATIONS.md` §2.4 in this repo for the design these fixtures exercise.
