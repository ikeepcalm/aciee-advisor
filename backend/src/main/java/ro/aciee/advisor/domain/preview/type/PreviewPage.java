package ro.aciee.advisor.domain.preview.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PreviewPage {
    HOME("Ghidul studentului de la ACIEE", "Ce îți cere de fapt fiecare curs",
            "Evaluări anonime și sfaturi de la studenți"),
    COURSES("Toate cursurile", "Cursurile de la ACIEE, fără surprize",
            "Examen, prezență, ore pe săptămână. Spuse de cei care au trecut pe acolo."),
    PROFESSORS("Profesori", "Profesorii, notați de studenți",
            "Predare, practică, notare. Anonim și la obiect."),
    TIMETABLE("Orar", "Orarul grupei tale",
            "Fără PDF-uri de descifrat. Alegi grupa și gata."),
    GUIDE("Ghid de anul I", "Credite, restanțe, burse, pe înțelesul tău",
            "Prezență, examene, taxe. Ce nu-ți spune nimeni în prima săptămână."),
    RATE("Evaluează", "Spune cum a fost la curs",
            "Răspunsurile tale rămân anonime. Îi ajuți pe cei care vin după tine."),
    PREVIEW("Tur de 30 de secunde", "ACIEE Advisor în 30 de secunde",
            "Orarul grupei tale, evaluări sincere ale cursurilor și un ghid de anul I, făcute de un student ACIEE. Anonim din construcție.");

    private final String eyebrow;
    private final String title;
    private final String note;
}
