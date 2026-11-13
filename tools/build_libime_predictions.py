"""Convert the bundled LibIME ARPA model into ranked Android lookup rows."""

import argparse
from collections import defaultdict
from hashlib import sha256
from pathlib import Path
import re
import tarfile


SOURCE_SHA256 = "ee83ecf20d52e8bccdba4cf6cd57183d53c257713a5eb77ee3a63d50fc3796dd"
HAN_WORD = re.compile(r"[\u4e00-\u9fff]{1,8}")
ROW_LIMIT = 24


def export(source: Path, output: Path) -> None:
    if sha256(source.read_bytes()).hexdigest() != SOURCE_SHA256:
        raise ValueError("Unexpected LibIME source archive checksum")

    unigrams = {}
    bigrams = {}
    next_words = defaultdict(dict)
    order = 0
    with tarfile.open(source, mode="r|zst") as archive:
        member = archive.next()
        if member is None or member.name != "lm_sc.arpa":
            raise ValueError("Missing LibIME ARPA model")
        model = archive.extractfile(member)
        if model is None:
            raise ValueError("Missing LibIME ARPA model")
        for raw_line in model:
            line = raw_line.decode("utf-8").strip()
            if line.endswith("-grams:"):
                order = int(line[1])
                continue
            if not order or not line or line.startswith("\\"):
                continue
            score, *words = line.split()
            score = float(score)
            words = words[:order]
            if not all(HAN_WORD.fullmatch(word) for word in words):
                continue

            if order == 1:
                if score >= -5.2:
                    unigrams[words[0]] = score
                continue

            if order == 2:
                first, candidate = words
                frequency = unigrams.get(first, float("-inf"))
                if frequency + score >= -6.15:
                    bigrams[first, candidate] = frequency + score
                if frequency < -5.2:
                    continue
            else:
                first, second, candidate = words
                if (first, second) not in bigrams:
                    continue

            if candidate not in unigrams or score < -2.9:
                continue
            context = "".join(words[:-1])
            if len(context) > 8:
                continue
            row = next_words[context]
            # Favor two-word histories when both orders form the same written context.
            row[candidate] = max(row.get(candidate, (0, 0.0)), (order, score))

    with output.open("w", encoding="utf-8", newline="\n") as index:
        for context, row in sorted(next_words.items()):
            candidates = sorted(row, key=lambda word: (-row[word][0], -row[word][1], word))
            index.write("\t".join([context, *candidates[:ROW_LIMIT]]) + "\n")
    print(f"{len(next_words):,} contexts, {output.stat().st_size:,} bytes")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="Original lm_sc.arpa-20250113.tar.zst archive")
    parser.add_argument("output", type=Path, help="Android TSV asset to write")
    args = parser.parse_args()
    export(args.source, args.output)
