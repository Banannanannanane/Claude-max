class_name Fmt
## Formatage des nombres à la française.

const SUFFIXES := ["", "k", "M", "Md", "Bn", "Bd", "Tn"]


static func num(v: float, dec := 0) -> String:
	var neg := v < 0.0
	var n := absf(v)
	var s := ""
	if n < 1000.0:
		if dec > 0 and n < 100.0 and absf(n - floorf(n)) > 0.0001:
			s = String.num(n, dec)
			if s.find(".") >= 0 and s.split(".")[1].length() < dec:
				s += "0"
		else:
			s = str(int(floorf(n)))
	else:
		var i := 0
		while n >= 1000.0 and i < SUFFIXES.size() - 1:
			n /= 1000.0
			i += 1
		s = (String.num(n, 2) if n < 10.0 else String.num(n, 1) if n < 100.0 else str(int(n))) + SUFFIXES[i]
	return ("-" if neg else "") + s.replace(".", ",")


## Nombre d'aiguilles réelles pour un nombre d'unités de jeu (1 unité = 1 000 aiguilles).
static func needles(units: float) -> String:
	return num(units * Data.NEEDLE_UNIT)


static func eur(v: float) -> String:
	return num(v, 2) + " €"


static func duration(sec: float) -> String:
	var s := int(sec)
	if s >= 3600:
		return "%d h %02d min" % [s / 3600, (s % 3600) / 60]
	return "%d min" % (s / 60)
