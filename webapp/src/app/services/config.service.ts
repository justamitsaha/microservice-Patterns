import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

@Injectable({
    providedIn: 'root'
})
export class ConfigService {

    private _apiBaseUrl: string = 'http://localhost:8085'; // default for local
    private _themeColor: string = 'blue';

    constructor(@Inject(PLATFORM_ID) private platformId: object) {
        if (isPlatformBrowser(this.platformId)) {
            const env = (window as any).__env;
            if (env?.apiBaseUrl) this._apiBaseUrl = env.apiBaseUrl;
            if (env?.appThemeColor) this._themeColor = env.appThemeColor;
        }
    }

    get apiBaseUrl(): string {
        return this._apiBaseUrl;
    }

    get themeColor(): string {
        return this._themeColor;
    }
}
