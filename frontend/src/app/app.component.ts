import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';

interface VooLatam {
  id?: number;
  origem: string;
  destino: string;
  dataVoo: string;
  horarioPartida: string;
  horarioChegada: string;
  duracao: string | null;
  preco: number;
  dataConsulta?: string;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  private readonly apiUrl = 'http://localhost:8080/api/voos';

  origem = 'GRU';
  destino = 'SDU';
  data = '2026-10-15';
  carregando = false;
  exportando = false;
  erro = '';
  voos: VooLatam[] = [];

  constructor(private readonly http: HttpClient) {}

  buscarVoos(): void {
    this.erro = '';
    this.carregando = true;

    const body = {
      origem: this.origem.trim().toUpperCase(),
      destino: this.destino.trim().toUpperCase(),
      data: this.data
    };

    this.http.post<VooLatam[]>(`${this.apiUrl}/buscar`, body).subscribe({
      next: (voos) => {
        this.voos = voos;
        this.carregando = false;
      },
      error: () => {
        this.erro = 'Nao foi possivel buscar os voos. Verifique o backend e tente novamente.';
        this.carregando = false;
      }
    });
  }

  exportarExcel(): void {
    this.erro = '';
    this.exportando = true;

    this.http.get(`${this.apiUrl}/exportar-excel`, {
      responseType: 'blob'
    }).subscribe({
      next: (arquivo) => {
        const url = window.URL.createObjectURL(arquivo);
        const link = document.createElement('a');

        link.href = url;
        link.download = 'voos-latam.xlsx';
        link.click();

        window.URL.revokeObjectURL(url);
        this.exportando = false;
      },
      error: () => {
        this.erro = 'Nao foi possivel exportar a planilha.';
        this.exportando = false;
      }
    });
  }
}
