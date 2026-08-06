"""Trainer graph scaffolding for SkillEdge."""


def build_trainer_node(trainer):
    return {
        "trainer_name": trainer.get("trainer_name") or trainer.get("TrainerName") or "",
        "email": trainer.get("official_email") or trainer.get("OffEmail") or "",
        "skills": trainer.get("skills") or [],
        "courses": trainer.get("current_courses") or [],
        "certifications": trainer.get("resume_certifications") or [],
        "qubit": trainer.get("avg_qubits_score") or trainer.get("qubit_score") or None,
        "interests": trainer.get("trainer_interests") or [],
    }


def get_trainer_signal(trainer):
    return build_trainer_node(trainer)
