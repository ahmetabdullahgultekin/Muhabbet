"""Measure and gain-normalise a clip with ffmpeg (linear gain, no dynamic loudnorm),
then encode OGG Vorbis (Android) and CAF + M4A AAC (iOS). Usage:
master.py <in.wav> <out_basename> <target_lufs> <fade_out_s> [trim_s]"""
import json, subprocess, sys, re, math
src, base, target, fade = sys.argv[1], sys.argv[2], float(sys.argv[3]), float(sys.argv[4])
trim = float(sys.argv[5]) if len(sys.argv) > 5 and sys.argv[5] != "-" else None
limit_db = float(sys.argv[6]) if len(sys.argv) > 6 else None

def run(args): return subprocess.run(args, capture_output=True, text=True)
def measure(path):
    # pad to >=0.4 s so clips shorter than one EBU R128 gating block still measure
    r = run(["ffmpeg","-hide_banner","-nostats","-i",path,"-af",
             "apad=whole_dur=0.4,loudnorm=print_format=json","-f","null","-"])
    j = json.loads(re.search(r"\{[^{}]*\}", r.stderr, re.S).group(0))
    return float(j["input_i"]), float(j["input_tp"])

pre = f"{base}_pre.wav"
dur = trim if trim else float(run(["ffprobe","-v","error","-show_entries","format=duration","-of","csv=p=0",src]).stdout)
chain = f"atrim=0:{dur},aformat=channel_layouts=mono,aresample=44100,afade=t=in:d=0.005,afade=t=out:st={dur-fade:.4f}:d={fade}"
if limit_db is not None:
    # boost then brick-wall limit to reduce crest factor of a very peaky transient
    chain += f",volume={limit_db}dB,alimiter=limit=0.891:attack=0.5:release=20:level=disabled"
run(["ffmpeg","-y","-v","error","-i",src,"-af",chain,"-c:a","pcm_s16le",pre])
i, tp = measure(pre)
gain = target - i
if tp + gain > -1.0:
    print(f"WARN true peak would be {tp+gain:.2f} dBTP; capping gain")
    gain = -1.0 - tp
final = f"{base}.wav"
run(["ffmpeg","-y","-v","error","-i",pre,"-af",f"volume={gain:.3f}dB","-c:a","pcm_s16le",final])
i2, tp2 = measure(final)
print(json.dumps({"file": final, "duration_s": round(dur,3), "gain_db": round(gain,2),
                  "lufs": i2, "true_peak_dbtp": tp2}))
for args in (["-c:a","libvorbis","-q:a","4", f"{base}.ogg"],
             ["-c:a","adpcm_ima_qt","-f","caf", f"{base}.caf"],
             ["-c:a","aac","-b:a","64k", f"{base}.m4a"]):
    r = run(["ffmpeg","-y","-v","error","-i",final,*args])
    if r.returncode: print("encode failed", args[-1], r.stderr[:300])
