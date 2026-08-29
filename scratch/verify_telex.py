"""
Simulate WMKeyboard's VietnameseEngine.transduce() in Python
to verify vi.txt entries match WMKeyboard's Telex behavior.
"""
import sys
sys.stdout.reconfigure(encoding='utf-8')
import unicodedata
import sys

# --- WMKeyboard Telex Engine (Python port) ---

class VMark:
    NONE = 0
    CIRCUMFLEX = 1
    BREVE = 2
    HORN = 3
    STROKE = 4

class VTone:
    NONE = 0
    ACUTE = 1    # s - sắc
    GRAVE = 2    # f - huyền
    HOOK = 3     # r - hỏi
    TILDE = 4    # x - ngã
    DOT = 5      # j - nặng

COMBINING = {
    VTone.ACUTE: '\u0301',
    VTone.GRAVE: '\u0300',
    VTone.HOOK: '\u0309',
    VTone.TILDE: '\u0303',
    VTone.DOT: '\u0323',
}

class VLetter:
    def __init__(self, base, mark=VMark.NONE, upper=False):
        self.base = base
        self.mark = mark
        self.upper = upper

def is_vowel(l):
    return l.base in 'aeiouy' or (l.base == 'w' and l.mark == VMark.HORN)

def precompose(base, mark):
    if mark == VMark.NONE: return base
    if mark == VMark.CIRCUMFLEX:
        return {'a': 'â', 'e': 'ê', 'o': 'ô'}.get(base, base)
    if mark == VMark.BREVE:
        return 'ă' if base == 'a' else base
    if mark == VMark.HORN:
        return {'o': 'ơ', 'u': 'ư', 'w': 'ư'}.get(base, base)
    if mark == VMark.STROKE:
        return 'đ' if base == 'd' else base
    return base

def nucleus(letters):
    vowels = [i for i, l in enumerate(letters) if is_vowel(l)]
    # qu- and gi- onsets
    if len(letters) >= 2 and letters[0].base == 'q' and letters[1].base == 'u':
        if any(v > 1 for v in vowels):
            vowels = [v for v in vowels if v != 1]
    if len(letters) >= 2 and letters[0].base == 'g' and letters[1].base == 'i':
        if any(v > 1 for v in vowels):
            vowels = [v for v in vowels if v != 1]
    if not vowels: return -1
    # Prefer marked vowel
    for v in reversed(vowels):
        if letters[v].mark != VMark.NONE:
            return v
    if len(vowels) == 1: return vowels[0]
    last = vowels[-1]
    has_coda = any(not is_vowel(letters[j]) for j in range(last + 1, len(letters)))
    if has_coda: return last
    if len(vowels) >= 3: return vowels[-2]
    a = letters[vowels[0]].base
    b = letters[vowels[1]].base
    if (a == 'o' and b == 'a') or (a == 'o' and b == 'e') or (a == 'u' and b == 'y'):
        return vowels[1]
    return vowels[0]

def render(letters, tone):
    if not letters: return ""
    nuc = -1 if tone == VTone.NONE else nucleus(letters)
    sb = []
    for i, l in enumerate(letters):
        c = precompose(l.base, l.mark)
        if l.upper:
            c = c.upper()
        sb.append(c)
        if i == nuc and tone in COMBINING:
            sb.append(COMBINING[tone])
    return unicodedata.normalize('NFC', ''.join(sb))

def apply_mark(letters, targets, mark):
    for i in range(len(letters) - 1, -1, -1):
        if letters[i].base in targets:
            letters[i].mark = VMark.NONE if letters[i].mark == mark else mark
            return True
    return False

def wmk_transduce(raw):
    """Port of WMKeyboard's VietnameseEngine.transduce() for Telex mode."""
    letters = []
    tone = VTone.NONE

    def toggle_tone(t):
        nonlocal tone
        tone = VTone.NONE if tone == t else t

    def has_vowel():
        return any(is_vowel(l) for l in letters)

    for ch in raw:
        upper = ch.isupper()
        lc = ch.lower()

        if lc in 'sfrxj':
            t = {'s': VTone.ACUTE, 'f': VTone.GRAVE, 'r': VTone.HOOK,
                 'x': VTone.TILDE, 'j': VTone.DOT}[lc]
            if has_vowel():
                if tone == t:
                    tone = VTone.NONE
                    letters.append(VLetter(lc, VMark.NONE, upper))
                else:
                    tone = t
            else:
                letters.append(VLetter(lc, VMark.NONE, upper))
        elif lc == 'w':
            u_idx = -1
            o_idx = -1
            for i in range(len(letters) - 1, -1, -1):
                if letters[i].base == 'u' and u_idx == -1: u_idx = i
                if letters[i].base == 'o' and o_idx == -1: o_idx = i
            if u_idx != -1 and o_idx != -1 and o_idx == u_idx + 1:
                toggle_off = letters[u_idx].mark == VMark.HORN and letters[o_idx].mark == VMark.HORN
                target_mark = VMark.NONE if toggle_off else VMark.HORN
                letters[u_idx].mark = target_mark
                letters[o_idx].mark = target_mark
            else:
                a = apply_mark(letters, 'a', VMark.BREVE) or \
                    apply_mark(letters, 'ouw', VMark.HORN)
                if not a:
                    letters.append(VLetter('w', VMark.HORN, upper))
        elif lc in 'aeo':
            last = letters[-1] if letters else None
            if last is not None and last.base == lc and last.mark == VMark.CIRCUMFLEX:
                last.mark = VMark.NONE
                letters.append(VLetter(lc, VMark.NONE, upper))
            else:
                applied = False
                for i in range(len(letters) - 1, -1, -1):
                    if letters[i].base == lc:
                        if letters[i].mark == VMark.NONE:
                            letters[i].mark = VMark.CIRCUMFLEX
                            applied = True
                        break
                if not applied:
                    letters.append(VLetter(lc, VMark.NONE, upper))
        elif lc == 'd':
            last = letters[-1] if letters else None
            if last is not None and last.base == 'd' and last.mark == VMark.STROKE:
                last.mark = VMark.NONE
                letters.append(VLetter('d', VMark.NONE, upper))
            else:
                applied = False
                for i in range(len(letters) - 1, -1, -1):
                    if letters[i].base == 'd':
                        if letters[i].mark == VMark.NONE:
                            letters[i].mark = VMark.STROKE
                            applied = True
                        break
                if not applied:
                    letters.append(VLetter('d', VMark.NONE, upper))
        else:
            letters.append(VLetter(lc, VMark.NONE, upper))

    return render(letters, tone)

# --- Laban reverse_telex (original from build_vi_dict.py) ---

TONES_LABAN = {
    '\u0301': 's',
    '\u0300': 'f',
    '\u0309': 'r',
    '\u0303': 'x',
    '\u0323': 'j',
}

HATS_LABAN = {
    'ă': 'aw', 'â': 'aa', 'ê': 'ee', 'ô': 'oo', 'ơ': 'ow', 'ư': 'uw', 'đ': 'dd',
    'Ă': 'Aw', 'Â': 'Aa', 'Ê': 'Ee', 'Ô': 'Oo', 'Ơ': 'Ow', 'Ư': 'Uw', 'Đ': 'Dd'
}

def laban_reverse_telex(word):
    nfd_word = unicodedata.normalize('NFD', word)
    tone_mark = ""
    base_word = ""
    for char in nfd_word:
        if char in TONES_LABAN:
            tone_mark = TONES_LABAN[char]
        else:
            base_word += char
    nfc_base = unicodedata.normalize('NFC', base_word)
    telex_word = ""
    for char in nfc_base:
        if char in HATS_LABAN:
            telex_word += HATS_LABAN[char]
        else:
            telex_word += char
    return telex_word + tone_mark

# --- Test: roundtrip every word in source dictionary ---

def main():
    import os
    src_file = os.path.join(os.path.dirname(__file__), '..', '..', 
                            'laban_extract', 'base', 'assets', 'list-syllable-special-words.txt')
    
    mismatches = []
    total = 0
    
    with open(src_file, 'r', encoding='utf-16') as fin:
        for line in fin:
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            parts = line.split('\t')
            if len(parts) != 2:
                continue
            
            word = parts[0]
            # Split multi-word entries
            syllables = word.split(' ')
            
            for syllable in syllables:
                total += 1
                telex_keys = laban_reverse_telex(syllable)
                composed = wmk_transduce(telex_keys)
                
                if composed != syllable.lower():
                    mismatches.append((syllable, telex_keys, composed))
    
    print(f"Total syllables tested: {total}")
    print(f"Mismatches: {len(mismatches)}")
    print()
    
    if mismatches:
        print("First 50 mismatches:")
        print(f"{'Original':<15} {'Telex Keys':<15} {'WMK Output':<15}")
        print("-" * 45)
        for orig, keys, out in mismatches[:50]:
            print(f"{orig:<15} {keys:<15} {out:<15}")
    
    # Also test specific user-reported cases
    print("\n\n=== User-reported test cases ===")
    test_cases = [
        ("ddax", "đã"),
        ("timf", "tìm"),
        ("toans", "toán"),
        ("rooif", "rồi"),
        ("oonr", "ổn"),
        ("ddaf", "đà"),
        ("ddas", "đá"),
        ("ddaj", "đạ"),
        ("thuongf", "thường"),  # ương test
        ("nguowif", "người"),   # ươi test
        ("chuwas", "chưa"),     # ưa test (note: chưa's telex should be "chuwa" + tone)
    ]
    
    print(f"{'Input':<15} {'Expected':<15} {'WMK Output':<15} {'Match?'}")
    print("-" * 55)
    for telex, expected in test_cases:
        result = wmk_transduce(telex)
        match = "✓" if result == expected else "✗"
        print(f"{telex:<15} {expected:<15} {result:<15} {match}")

if __name__ == '__main__':
    main()
