from dataclasses import dataclass


@dataclass
class PenaltyRange:

    min: str = "N/A"
    max: str = "N/A"

    def to_dict(self):
        return {"min": self.min, "max": self.max}

    def __str__(self):
        return f"{self.min} - {self.max}"
